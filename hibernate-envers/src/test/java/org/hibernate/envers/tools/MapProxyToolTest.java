/**
 * 
 */
package org.hibernate.envers.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Test;

/**
 * @author Felix Feisst (feisst dot felix at gmail dot com)
 */
public class MapProxyToolTest {

	@Test
	public void testIsCompatible() {
		class TestClass {

			public String getProp1() {
				return "";
			}

			public void setProp1(final String string) {
			}

			public int getProp2() {
				return 0;
			}

			public void setProp2(final int value) {
			}

			public void setProp3(final long value) {
			}

			public boolean getProp4() {
				return false;
			}
		}
		final Map<String, Class<?>> props = new HashMap<String, Class<?>>();
		props.put( "prop1", String.class );
		props.put( "prop2", int.class );
		assertTrue( "Expected prop1, prop2 to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );

		props.clear();
		props.put( "prop1", String.class );
		assertTrue( "Expected prop1 to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );

		props.clear();
		props.put( "prop2", int.class );
		assertTrue( "Expected prop2 to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );

		props.clear();
		props.put( "prop1", long.class );
		assertFalse( "Expected prop1 not to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );
		props.put( "prop2", int.class );
		assertFalse( "Expected prop1 not to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );

		props.clear();
		props.put( "foo", int.class );
		assertFalse( "Expected foo not to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );
		props.put( "prop1", String.class );
		props.put( "prop2", int.class );
		assertFalse( "Expected foo not to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );

		props.clear();
		props.put( "prop3", long.class );
		assertFalse( "Expected prop3 not to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );
		props.put( "prop1", String.class );
		props.put( "prop2", int.class );
		assertFalse( "Expected prop3 not to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );

		props.clear();
		props.put( "prop4", boolean.class );
		assertFalse( "Expected prop4 not to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );
		props.put( "prop1", String.class );
		props.put( "prop2", int.class );
		assertFalse( "Expected prop4 not to be compatible", MapProxyTool.isCompatible( TestClass.class, props ) );
	}

	@Test
	public void testResolveCompatibleClass() {
		class Prop1 {

			public int getProp1() {
				return 0;
			}

			public void setProp1(final int value) {
			}
		}
		class Prop2 {

			public String getProp2() {
				return "";
			}

			public void setProp2(final String value) {
			}
		}
		SortedMap<Long, Class<?>> map = new TreeMap<Long, Class<?>>();
		Map<String, Class<?>> props = new HashMap<String, Class<?>>();
		props.put( "prop1", int.class );
		assertNull( "Expected no class from empty map", MapProxyTool.resolveCompatibleClass( map, 3L, props ) );
		map.put( 1L, Prop1.class );
		map.put( 100L, Prop2.class );
		assertEquals( "Expected Class Prop1 to be returned", Prop1.class, MapProxyTool.resolveCompatibleClass( map, 3L, props ) );
		props.clear();
		props.put( "prop2", String.class );
		assertEquals( "Expected Class Prop2 to be returned", Prop2.class, MapProxyTool.resolveCompatibleClass( map, 3L, props ) );
		props.put( "prop1", int.class );
		assertNull( "Expected no class from non compatible classes to be returned", MapProxyTool.resolveCompatibleClass( map, 3L, props ) );

		map.clear();
		map.put( 5L, Prop1.class );
		props.clear();
		props.put( "prop1", int.class );
		assertEquals( "Expected Class Prop1 to be returned", Prop1.class, MapProxyTool.resolveCompatibleClass( map, 10L, props ) );
		assertEquals( "Expected Class Prop1 to be returned", Prop1.class, MapProxyTool.resolveCompatibleClass( map, 1L, props ) );
		props.put( "prop2", String.class );
		assertNull( "Expected no class to be returned", MapProxyTool.resolveCompatibleClass( map, 10L, props ) );
		assertNull( "Expected no class to be returned", MapProxyTool.resolveCompatibleClass( map, 1L, props ) );
	}
}
