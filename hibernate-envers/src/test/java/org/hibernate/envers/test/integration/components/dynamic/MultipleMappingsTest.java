/**
 * 
 */
package org.hibernate.envers.test.integration.components.dynamic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.envers.test.BaseEnversFunctionalTestCase;
import org.hibernate.envers.tools.MapProxyTool;
import org.junit.Test;

/**
 * @author Felix Feisst (feisst dot felix at gmail dot com)
 */
public class MultipleMappingsTest extends BaseEnversFunctionalTestCase {

	private long schemaVersion = 0;

	private boolean createSchema = true;

	@Override
	protected boolean createSchema() {
		return createSchema;
	}

	@Override
	protected void configure(Configuration configuration) {
		super.configure( configuration );
		if ( createSchema ) {
			// do not drop schema after session factory is closed
			configuration.setProperty( Environment.HBM2DDL_AUTO, "create" );
		}
		configuration.setProperty( MapProxyTool.SCHEMA_VERSION_PROPERTY, String.valueOf( schemaVersion ) );
	}

	/**
	 * Provide mapping with all properties, such that the automatic schema generation will generate all necessary
	 * columns. For the other two mappings, automatic schema generation is disabled.
	 */
	private String[] mappings = new String[] { "mappings/dynamicComponents/multiMapping0.hbm.xml" };

	@Override
	protected String[] getMappings() {
		return mappings;
	}

	@Override
	protected boolean rebuildSessionFactoryOnError() {
		return false;
	}

	@Test
	public void testMultipleMappings() {
		createSchema = false;
		mappings = new String[] { "mappings/dynamicComponents/multiMapping1.hbm.xml" };
		getSession().close();
		releaseSessionFactory();
		schemaVersion = 0;

		AuditedDynamicComponentEntity entity;
		buildSessionFactory();
		getSession().getTransaction().begin();
		entity = new AuditedDynamicComponentEntity();
		entity.getCustomFields().put( "multiprop1", 4711 );
		entity.getCustomFields().put( "multiprop2", 3141 );
		getSession().persist( entity );
		getSession().getTransaction().commit();

		AuditedDynamicComponentEntity rev1 = getAuditReader().find( AuditedDynamicComponentEntity.class, entity.getId(), 1 );
		assertEquals( "Unexpected value for multiprop1", 4711, rev1.getCustomFields().get( "multiprop1" ) );
		assertEquals( "Unexpected value for multiprop2", 3141, rev1.getCustomFields().get( "multiprop2" ) );
		assertNull( "Unexpected value for multiprop3", rev1.getCustomFields().get( "multiprop3" ) );
		getSession().close();
		releaseSessionFactory();

		mappings = new String[] { "mappings/dynamicComponents/multiMapping2.hbm.xml" };
		schemaVersion = 1;
		buildSessionFactory();

		getSession().getTransaction().begin();
		entity = (AuditedDynamicComponentEntity) getSession().byId( AuditedDynamicComponentEntity.class ).load( entity.getId() );
		entity.getCustomFields().put( "multiprop3", 42 );
		getSession().getTransaction().commit();

		AuditedDynamicComponentEntity rev2 = getAuditReader().find( AuditedDynamicComponentEntity.class, entity.getId(), 2 );
		assertEquals( "Unexpected value for multiprop1", 4711, rev2.getCustomFields().get( "multiprop1" ) );
		assertNull( "Unexpected value for multiprop2", rev2.getCustomFields().get( "multiprop2" ) );
		assertEquals( "Unexpected value for multiprop3", 42, rev2.getCustomFields().get( "multiprop3" ) );
	}

}
