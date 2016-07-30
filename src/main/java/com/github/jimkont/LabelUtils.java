package com.github.jimkont;

import org.aksw.rdfunit.prefix.LOVEndpoint;
import org.aksw.rdfunit.prefix.SchemaEntry;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.jimkont.EscapeUtils.escapeStringValue;

/**
 * @author Dimitris Kontokostas
 * @since 29/7/2016 11:07 μμ
 */
public final class LabelUtils {
    private LabelUtils(){}

    private static Set<String> stringValueDatatypes = new HashSet<>(Arrays.asList(
            RDF.langString.getURI(), "http://www.w3.org/2001/XMLSchema#string"));

    private static final Map<String, String> lovPrefixes = createLOVPrefixes();
    private static Map<String, String> createLOVPrefixes() {
        return new LOVEndpoint().getAllLOVEntries().stream()
                .distinct()
                .collect(Collectors.toMap(SchemaEntry::getVocabularyNamespace, SchemaEntry::getPrefix, (p1, p2) -> p1));
    }

    /**
     * return the local name unless it is a lang string and return localName_lang
     */
    public static String getPropertyLabel(Property property, RDFNode node) {
        String name = getPropertyLabel(property);
        if (node.isLiteral() ) {
            String lang = node.asLiteral().getLanguage();
            if (!lang.isEmpty()) {
                return name + "_" + lang;
            }
        }
        return name;
    }

    public static String getPropertyLabel(Property property) {
        return getPrefixPrefix(property.asResource()) + property.getLocalName();
    }

    /**
     * get the value of a literal
     * TODO check & align all datatypes
     */
    public static String getLiteralValue(RDFNode node) {
        String value = node.toString();
        if (node.isLiteral()) {
            Literal nodeL = node.asLiteral();
            value = nodeL.getLexicalForm();
            if (stringValueDatatypes.contains(nodeL.getDatatypeURI())) {
                value = "\"" + escapeStringValue(value) + "\"";
            }
        }
        return value;
    }

    /**
     * if resource has a type return localName:Type, if not type return localNAme
     * for now return error when multiple types are defined
     */
    public static String getResourceLabelWithType(Resource resource) {

        String localName = getResourceLabel(resource);

        // Get resource types
        List<Resource> resourceTypes = resource.listProperties(RDF.type).toList().stream()
                .map(Statement::getObject)
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .filter(r -> !r.isAnon())
                .collect(Collectors.toList());

        switch (resourceTypes.size()) {
            case 0 :
                return localName;
            case 1 :
                return localName + ":" + getResourceLabel(resourceTypes.get(0));
            // when multiple try to get the most direct one from rdfs:subClassOf
            default:
                return localName;
            //throw new IllegalArgumentException("multiple types");
        }
    }

    private static String consolidateMultipleClasses(List<Resource> classes) {
        return classes.stream()
                .filter(c -> c != OWL.Thing)
                //TODO filter superclasses
                .map(LabelUtils::getResourceLabel)
                .findFirst()
                .orElse("");


    }

    /**
     *get a lit of all superclasses
     * TODO fix hierarchy loops
     */
    private static List<Resource> getSuperClasses(Resource resource) {
        List<Resource> superClasses = new LinkedList<>();

        // get direct superclasses
        List<Resource> temp = resource.listProperties(RDFS.subClassOf).toList().stream()
                .map(Statement::getObject)
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .collect(Collectors.toList());

        temp.forEach( c -> {
            superClasses.add(c);
            superClasses.addAll(getSuperClasses(c));
        });

        return superClasses;
    }

    /**
     *Get a simple label for a resource, if it is a blank node get an id based label
     */
    public static String getResourceLabel(Resource resource) {
        String resourceLabel;
        if (resource.isAnon()) {
            resourceLabel = "BN"+resource.getId().getLabelString();
        } else {
            resourceLabel = getPrefixPrefix(resource) + resource.getLocalName();
        }
        return EscapeUtils.escapeNodeName(resourceLabel);
    }

    /**
     * get a label prefix for a namespace prefix e.g. 'rdfs_' in rdfs_label
     */
    public static String getPrefixPrefix(Resource resource) {
        String ns = resource.getNameSpace();
        String prefix = resource.getModel().getNsURIPrefix(ns);
        if (prefix == null ) {
            // try with LOV if not defined locally
            // or give a numeric based ID
            if (lovPrefixes.containsKey(ns)) {
                return lovPrefixes.get(ns) + "_";
            } else {

                return "p" + ns.hashCode() + "_";
            }
        }
        if (prefix.isEmpty()) {
            return "";
        }

        return prefix + "_";
    }
}
