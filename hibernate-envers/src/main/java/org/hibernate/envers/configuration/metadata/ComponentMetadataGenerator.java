package org.hibernate.envers.configuration.metadata;

import java.util.Iterator;
import java.util.Map;

import org.dom4j.Element;
import org.hibernate.envers.configuration.metadata.reader.ComponentAuditingData;
import org.hibernate.envers.configuration.metadata.reader.PropertyAuditingData;
import org.hibernate.envers.entities.mapper.CompositeMapperBuilder;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

/**
 * Generates metadata for components.
 * 
 * @author Adam Warski (adam at warski dot org)
 * @author Lukasz Zuchowski (author at zuchos dot com)
 * @author Felix Feisst (feisst at patronas dot de)
 */
public final class ComponentMetadataGenerator {

	private final AuditMetadataGenerator mainGenerator;

	ComponentMetadataGenerator(AuditMetadataGenerator auditMetadataGenerator) {
		mainGenerator = auditMetadataGenerator;
	}

	@SuppressWarnings({ "unchecked" })
	public void addComponent(Element parent, PropertyAuditingData propertyAuditingData, Value value, CompositeMapperBuilder mapper, String entityName,
			EntityXmlMappingData xmlMappingData, boolean firstPass) {
		Component propComponent = (Component) value;

		final String componentClassName;
		if ( propComponent.isDynamic() ) {
			componentClassName = Map.class.getCanonicalName();
		}
		else {
			componentClassName = propComponent.getComponentClassName();
		}

		CompositeMapperBuilder componentMapper = mapper.addComponent( propertyAuditingData.getPropertyData(), componentClassName );

		// The property auditing data must be for a component.
		ComponentAuditingData componentAuditingData = (ComponentAuditingData) propertyAuditingData;

		// Adding all properties of the component
		Iterator<Property> properties = propComponent.getPropertyIterator();
		while ( properties.hasNext() ) {
			Property property = properties.next();

			PropertyAuditingData componentPropertyAuditingData = componentAuditingData.getPropertyAuditingData( property.getName() );

			// Checking if that property is audited
			if ( componentPropertyAuditingData != null ) {
				mainGenerator.addValue( parent, property.getValue(), componentMapper, entityName, xmlMappingData, componentPropertyAuditingData,
						property.isInsertable(), firstPass, false );
			}
		}
	}
}
