package org.hibernate.envers.tools;

import static org.hibernate.envers.tools.StringTools.capitalizeFirst;
import static org.hibernate.envers.tools.StringTools.getLastComponent;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.NotFoundException;

import org.hibernate.envers.entities.PropertyData;
import org.hibernate.envers.internal.EnversMessageLogger;
import org.hibernate.envers.tools.reflection.ReflectionTools;
import org.hibernate.service.classloading.spi.ClassLoaderService;
import org.hibernate.service.classloading.spi.ClassLoadingException;
import org.jboss.logging.Logger;

/**
 * @author Lukasz Zuchowski (author at zuchos dot com)
 * @author Felix Feisst (author at patronas dot de)
 */
public class MapProxyTool {

	public static final String SCHEMA_VERSION_PROPERTY = "org.hibernate.envers.tools.schema_version";

	private static final EnversMessageLogger LOG = Logger.getMessageLogger( EnversMessageLogger.class, MapProxyTool.class.getName() );

	/**
	 * @param className Name of the class to construct (should be unique within class loader)
	 * @param map instance that will be proxied by java bean
	 * @param propertyDatas properties that should java bean declare
	 * @param classLoaderService
	 * @return new instance of proxy
	 * @author Lukasz Zuchowski (author at zuchos dot com) Creates instance of map proxy class. This proxy class will be
	 * a java bean with properties from <code>propertyDatas</code>. Instance will proxy calls to instance of the map
	 * passed as parameter.
	 */
	public static Object newInstanceOfBeanProxyForMap(String className, Map<String, Object> map, Set<PropertyData> propertyDatas,
			ClassLoaderService classLoaderService, Long schemaVersion) {
		Map<String, Class<?>> properties = prepareProperties( propertyDatas );
		return createNewInstance( map, classForName( className, properties, classLoaderService, schemaVersion ) );
	}

	private static Object createNewInstance(Map<String, Object> map, Class aClass) {
		try {
			return aClass.getConstructor( Map.class ).newInstance( map );
		}
		catch (Exception e) {
			throw new RuntimeException( e );
		}
	}

	private static Map<String, Class<?>> prepareProperties(Set<PropertyData> propertyDatas) {
		Map<String, Class<?>> properties = new HashMap<String, Class<?>>();
		for ( PropertyData propertyData : propertyDatas ) {
			properties.put( propertyData.getBeanName(), Object.class );
		}
		return properties;
	}

	private static Class loadClass(String className, ClassLoaderService classLoaderService) {
		try {
			return ReflectionTools.loadClass( className, classLoaderService );
		}
		catch (ClassLoadingException e) {
			return null;
		}

	}

	private static final Map<String, SchemaVersionMap> schemaVersionMap = new ConcurrentHashMap<String, SchemaVersionMap>();

	private static class SchemaVersionMap {

		private final ReadWriteLock lock = new ReentrantReadWriteLock();
		private final SortedMap<Long, Class<?>> map = new TreeMap<Long, Class<?>>();
	}

	/**
	 * Null-safe and thread-safe getter for the schema version map for the specified class name.
	 */
	private static SchemaVersionMap getSchemaVersionMap(final String className) {
		SchemaVersionMap result = schemaVersionMap.get( className );
		if ( result == null ) {
			synchronized ( schemaVersionMap ) {
				result = schemaVersionMap.get( className );
				if ( result == null ) {
					result = new SchemaVersionMap();
					schemaVersionMap.put( className, result );
				}
			}
		}
		return result;
	}

	/**
	 * Generates/loads proxy class for given name with properties for map.
	 * 
	 * @param className name of the class that will be generated/loaded
	 * @param properties list of properties that should be exposed via java bean
	 * @param classLoaderService
	 * @return proxy class that wraps map into java bean
	 */
	public static Class<?> classForName(String className, Map<String, Class<?>> properties, ClassLoaderService classLoaderService, Long schemaVersion) {
		Class<?> result;
		if ( schemaVersion == null ) {
			LOG.debugf( "Resolve map proxy class %s (no schema version is set)", className );
			result = loadClass( className, classLoaderService );
			if ( result == null ) {
				result = generate( className, properties );
			}
		}
		else {
			LOG.debugf( "Resolving map proxy class %s for schema version %s", className, schemaVersion );
			result = classForNameForSchemaVersion( className, schemaVersion, properties );
		}
		return result;
	}

	/**
	 * Loads a class according to the specified schema version.
	 */
	private static Class<?> classForNameForSchemaVersion(final String rawClassName, final Long schemaVersion, final Map<String, Class<?>> properties) {
		SchemaVersionMap svm = getSchemaVersionMap( rawClassName );
		svm.lock.readLock().lock();
		try {
			SortedMap<Long, Class<?>> map = svm.map;
			Class<?> result = map.get( schemaVersion );
			if ( result == null ) {
				// lock upgrading is not possible
				svm.lock.readLock().unlock();
				svm.lock.writeLock().lock();
				try {
					result = map.get( schemaVersion );
					if ( result == null ) {
						LOG.debugf( "No map proxy class has been set for class name %s and schema version %s", rawClassName, schemaVersion );
						result = resolveCompatibleClass( map, schemaVersion, properties );
						if ( result == null ) {
							final String className = rawClassName.concat( "Version" ).concat( String.valueOf( schemaVersion ) );
							LOG.debugf( "Found no compatible class for class name %s and schema version %s. Generating new map proxy class with name %s",
									rawClassName, schemaVersion, className );
							result = generate( className, properties );
						}
						map.put( schemaVersion, result );
					}
				}
				finally {
					// lock downgrading is possible
					svm.lock.readLock().lock();
					svm.lock.writeLock().unlock();
				}
			}
			return result;
		}
		finally {
			svm.lock.readLock().unlock();
		}
	}

	/**
	 * Resolves a compatible class from the next lower or next higher schema version. If there is no such schema version
	 * or the none of the two classes is compatible, <code>null</code> is returned.
	 * <p>
	 * Protected for testing only.
	 */
	protected static Class<?> resolveCompatibleClass(final SortedMap<Long, Class<?>> map, final long schemaVersion, final Map<String, Class<?>> properties) {
		Class<?> result = null;
		final SortedMap<Long, Class<?>> headMap = map.headMap( schemaVersion );
		if ( !headMap.isEmpty() ) {
			final Long smallerSchemaVersion = headMap.lastKey();
			Class<?> candidate = headMap.get( smallerSchemaVersion );
			if ( isCompatible( candidate, properties ) ) {
				LOG.debugf( "Found compatible class in schema version %s", smallerSchemaVersion );
				result = candidate;
			}
		}
		if ( result == null ) {
			final SortedMap<Long, Class<?>> tailMap = map.tailMap( schemaVersion );
			if ( !tailMap.isEmpty() ) {
				final Long greaterSchemaVersion = tailMap.firstKey();
				Class<?> candidate = tailMap.get( greaterSchemaVersion );
				if ( isCompatible( candidate, properties ) ) {
					LOG.debugf( "Found compatible class in schema version %s", greaterSchemaVersion );
					result = candidate;
				}
			}
		}
		return result;
	}

	/**
	 * Checks whether the specified class is compatible with the specified properties.
	 * <p>
	 * Protected for testing only.
	 */
	protected static boolean isCompatible(final Class<?> clazz, final Map<String, Class<?>> properties) {
		boolean result = true;
		for ( Entry<String, Class<?>> entry : properties.entrySet() ) {
			final String capitalizedProperty = capitalizeFirst( entry.getKey() );
			final String getterName = "get".concat( capitalizedProperty );
			final String setterName = "set".concat( capitalizedProperty );
			try {
				Method getter = clazz.getMethod( getterName );
				result = getter.getReturnType().equals( entry.getValue() );
				Method setter = clazz.getMethod( setterName, entry.getValue() );
				result = result && setter.getReturnType().equals( void.class );
			}
			catch (NoSuchMethodException e) {
				result = false;
			}
			catch (SecurityException e) {
				result = false;
			}
			if ( !result ) {
				break;
			}
		}
		return result;
	}

	/**
	 * Protected for test only
	 */
	protected static Class generate(String className, Map<String, Class<?>> properties) {
		try {
			ClassPool pool = ClassPool.getDefault();
			CtClass cc = pool.makeClass( className );

			cc.addInterface( resolveCtClass( Serializable.class ) );
			cc.addField( new CtField( resolveCtClass( Map.class ), "theMap", cc ) );
			cc.addConstructor( generateConstructor( className, cc ) );

			for ( Entry<String, Class<?>> entry : properties.entrySet() ) {

				// add getter
				cc.addMethod( generateGetter( cc, entry.getKey(), entry.getValue() ) );

				// add setter
				cc.addMethod( generateSetter( cc, entry.getKey(), entry.getValue() ) );
			}
			return cc.toClass();
		}
		catch (Exception e) {
			throw new RuntimeException( e );
		}
	}

	private static CtConstructor generateConstructor(String className, CtClass cc) throws NotFoundException, CannotCompileException {
		StringBuffer sb = new StringBuffer();
		sb.append( "public " ).append( getLastComponent( className ) ).append( "(" ).append( Map.class.getName() ).append( " map)" ).append( "{" )
				.append( "this.theMap = map;" ).append( "}" );
		System.out.println( sb );
		return CtNewConstructor.make( sb.toString(), cc );
	}

	private static CtMethod generateGetter(CtClass declaringClass, String fieldName, Class fieldClass) throws CannotCompileException {

		String getterName = "get" + capitalizeFirst( fieldName );

		StringBuilder sb = new StringBuilder();
		sb.append( "public " ).append( fieldClass.getName() ).append( " " ).append( getterName ).append( "(){" ).append( "return (" )
				.append( fieldClass.getName() ).append( ")this.theMap.get(\"" ).append( fieldName ).append( "\")" ).append( ";" ).append( "}" );
		return CtMethod.make( sb.toString(), declaringClass );
	}

	private static CtMethod generateSetter(CtClass declaringClass, String fieldName, Class fieldClass) throws CannotCompileException {

		String setterName = "set" + capitalizeFirst( fieldName );

		StringBuilder sb = new StringBuilder();
		sb.append( "public void " ).append( setterName ).append( "(" ).append( fieldClass.getName() ).append( " " ).append( fieldName ).append( ")" )
				.append( "{" ).append( "this.theMap.put(\"" ).append( fieldName ).append( "\"," ).append( fieldName ).append( ")" ).append( ";" ).append( "}" );
		return CtMethod.make( sb.toString(), declaringClass );
	}

	private static CtClass resolveCtClass(Class clazz) throws NotFoundException {
		return resolveCtClass( clazz.getName() );
	}

	private static CtClass resolveCtClass(String clazz) throws NotFoundException {
		try {
			ClassPool pool = ClassPool.getDefault();
			return pool.get( clazz );
		}
		catch (NotFoundException e) {
			return null;
		}
	}

}
