/*******************************************************************************
 * Copyright (c) 2011 University of Southampton.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package ac.soton.eventb.emf.core.extension.navigator.refiner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil.Copier;
import org.eclipse.gmf.runtime.emf.core.util.EMFCoreUtil;
import org.eventb.emf.core.CorePackage;
import org.eventb.emf.core.EventBElement;
import org.eventb.emf.core.EventBNamed;
import org.eventb.emf.core.EventBNamedCommentedComponentElement;
import org.eventb.emf.core.EventBObject;

/**
 * Abstract Element Refiner for EventB EMF Extensions.
 * This should be extended for each Extension ID
 * 
 * @author cfsnook
 *
 */
public abstract class AbstractElementRefiner {

	/**
	 * A list of the EClasses which should not be copied into the refinement
	 */
	private	List<EClass> filterList = new ArrayList<EClass>();

	/**
	 * Extenders provide this method to populate the list of EClasses 
	 * which should not be copied into a refinement.
	 *  
	 * @param filterList
	 */
	protected abstract void populateFilterByTypeList(List<EClass> filterList);
	
	/**
	 * This enumeration gives the options for dealing with references
	 * CHAIN = the refined reference will target the abstract parent object that contained the abstract reference
	 * EQUIV = the refined reference will target the refined version of the target of the abstract reference if it exists,
	 *  when no refined version of the target exists (e.g. where the target is a filtered class or in another component) then this option acts like COPY
	 * COPY = the refined reference will target the exact same object that the abstract reference did
	 * DROP = the refined reference is left unset (this is the default behaviour if no entry is given for a reference feature)
	 *
	 */
	public enum RefHandling {
		COPY,CHAIN,EQUIV,DROP
	}
	
	/**
	 * A map of the references which need to be dealt with in the new refined model.
	 * For each EReference, the RefHandling indicates whether it should be dealt with as a reference back to 
	 * the source element (e.g. refines) or a normal reference within the same resource level which
	 * will be copied to simulate the abstract one, or it should be copied or dropped in the refined model.
	 */
	private Map<EReference,RefHandling> referencemap = new HashMap<EReference,RefHandling>();
	
	/**
	 * Extenders provide this method to populate the reference mapping with a list of 
	 * EReference features in their model extension. For each one indicate whether it should be dealt 
	 * with as a reference to the original source element (e.g. refines).
	 * 
	 */
	protected abstract void populateReferenceMap(Map<EReference,RefHandling> referencemap);
	
	/**
	 * This gets an object from the contents of the concrete parent which is considered to be equivalent 
	 * to the given abstract object. 
	 * 
	 * (In this context, equivalence means that if a reference in the abstract model refers to the abstract object,
	 * then a corresponding reference in the concrete model should refer to the returned equivalent object).
	 *
	 * This default implementation returns the first element, of the same class, discovered with the same name as the abstract object or,
	 * failing that, the first element of the same class discovered with a refines relationship to the  abstract object.
	 * 
	 * Extenders may override this default implementation to give other equivalances.
	 * 
	 * @param concreteParent
	 * @param abstractObject
	 * @return
	 */
	public EventBObject getEquivalentObject(EObject concreteParent, EObject abstractObject) {
		return getEquivalentObject(concreteParent, null, abstractObject);
	}
	
	public EventBObject getEquivalentObject(EObject concreteParent, EStructuralFeature feature, EObject abstractObject) {

		if (abstractObject instanceof EventBNamedCommentedComponentElement && concreteParent.eClass()==abstractObject.eClass()){
			return (EventBObject) concreteParent;
		}
		
		List<Object> contents = new ArrayList<Object>();
		contents.add(concreteParent);
		if (feature != null){
			Object featureValue =	concreteParent.eGet(feature);
			if (featureValue instanceof EList<?>) {
				contents.addAll(((EList<?>) concreteParent.eGet(feature)));
				//contents = ((EList<?>) concreteParent.eGet(feature)).iterator();
			}
		}else{
			Iterator<EObject> iter = concreteParent.eAllContents();
			while (iter.hasNext()){
				contents.add(iter.next());
			}
		}

		EClass clazz = abstractObject.eClass();
		EStructuralFeature nameFeature = clazz.getEStructuralFeature("name");
		Object name = nameFeature==null? null : abstractObject.eGet(nameFeature);
//		EStructuralFeature internalIdFeature = clazz.getEStructuralFeature("internalId");
//		Object internalId = internalIdFeature==null? null : abstractObject.eGet(internalIdFeature);
		EStructuralFeature refinesFeature = clazz.getEStructuralFeature("refines");
		

		for (Object possible : contents){
			if (possible instanceof EObject && ((EObject) possible).eClass() == clazz){
				
				// if name is the same and in the same equivalent parent
				if (nameFeature!=null && name!=null && name.equals(((EObject) possible).eGet(nameFeature))){
					if (abstractObject.eIsProxy()){
						ResourceSet rs = concreteParent.eResource().getResourceSet();
						abstractObject = EcoreUtil.resolve(abstractObject, rs);
					}
					EObject abstractsParent = abstractObject.eContainer();
					// get a refiner for the ePackage containing this abstract object
					String nsURI = abstractsParent.eClass().getEPackage().getNsURI();
					AbstractElementRefiner refiner = ElementRefinerRegistry.getRegistry().getRefiner(nsURI);
					if (refiner==null) continue;
					EventBObject equivalentParent = refiner.getEquivalentObject(concreteParent, abstractsParent);
					if (((EObject) possible).eContainer() == equivalentParent){
						return (EventBObject) possible;
					}
				}

				// if refines the abstract object
				if (refinesFeature!=null && !refinesFeature.isMany() && ((EObject) possible).eGet(refinesFeature) == abstractObject){
					return (EventBObject) possible;
				}
				
			}
		}
		return null;
	}
		
	/**
	 * Used to get the key (source element) from the copier map using the value (target element)
	 * 
	 * @param map
	 * @param value
	 * @return
	 */
	private static <T, E> T getKeyByValue(Map<T, E> map, E value) {
	    for (Entry<T, E> entry : map.entrySet()) {
	        if (value.equals(entry.getValue())) {
	            return entry.getKey();
	        }
	    }
	    return null;
	}
	

	/**
	 * Constructor.
	 */
	protected AbstractElementRefiner() {
		populateFilterByTypeList(filterList);
		populateReferenceMap(referencemap);
	}


	/**
	 * Creates a refined component element from the given abstract one.
	 * 
	 * The abstract component must be contained in a resource.
	 * 
	 * @param abstract component element
	 * @return
	 */
	public EventBNamedCommentedComponentElement refine(EventBNamedCommentedComponentElement abstractElement, URI concreteResourceURI, String concreteComponentName) {
		return (EventBNamedCommentedComponentElement) refine(null, abstractElement, null, concreteResourceURI, concreteComponentName);
	}

	/**
	 * Creates a refined element from the given abstract one and a separate abstract URI.
	 * In this case the abstract element need not be contained in the abstract component.
	 * It will be used (copied) to make a refined element but any references to the abstract element 
	 * (e.g. refines) will be based on the given abstract URI.
	 * 
	 * The abstract component must be contained in a resource.
	 * 
	 * @param abstract component element
	 * @return
	 */
	public EventBElement refine(URI abstractUri, EventBObject abstractElement, EventBNamedCommentedComponentElement concreteComponent) {
		return refine(abstractUri, abstractElement, concreteComponent, null, null);
	}
	
	/**
	 * Creates a refined element from the given abstract one.
	 * 
	 * The abstract component must be contained in a resource.
	 * 
	 * @param abstract component element
	 * @return
	 */
	public EventBElement refine(EventBObject abstractElement,  EventBNamedCommentedComponentElement concreteComponent){
		return refine(null, abstractElement, concreteComponent, null, null);
	}

		
	/**
	 * Creates a refined element from the given abstract one.
	 * 
	 * The abstract element must be contained in a resource.
	 * 
	 * Optionally, a containing concrete Component may be given in order to find equivalent reference targets
	 * when there may be references that target elements outside of the newly created concrete elements. 
	 * If all EQUIV references are contained within the copied element (e.g. for refining a complete component) this may be null.
	 * 
	 * @param abstract element
	 * @return
	 */
	private EventBElement refine(URI abstractUri, EventBObject abstractElement,  EventBNamedCommentedComponentElement concreteComponent, URI concreteResourceURI, String concreteComponentName) {
		if (abstractUri==null){
			abstractUri = EcoreUtil.getURI(abstractElement);
		}
		if (concreteComponentName==null && concreteComponent!=null ){
			concreteComponentName = concreteComponent.getName();
		}
		if (concreteResourceURI==null && concreteComponent!=null ){
			concreteResourceURI = EcoreUtil.getURI(concreteComponent);
		}
		
		Copier copier = new Copier(true,false);
		// create refined Component using copier.
		// this does a deep copy of all the children and properties of the copied element
		// but it does not copy any references
		EventBElement concreteEventBElement = (EventBElement) copier.copy(abstractElement); 
		//copier.copyReferences();  <--THIS DOES NOT WORK - INSTEAD SEE BELOW
		copyReferences(abstractUri, concreteEventBElement, copier, concreteResourceURI, concreteComponent, concreteComponentName);
		//having copied everything we may need to remove some kinds of elements that are not supposed to be
		//copied into a refinement
		filterElements(concreteEventBElement);
		return concreteEventBElement;
	}

	/*
	 * This removes any elements that are of a type (EClass) listed in filterList
	 */
	@SuppressWarnings("unchecked")
	private void filterElements(EventBElement concreteEventBElement) {
		List<EObject> removeList = new ArrayList<EObject>();
		for (EClass removeClass : filterList){
			removeList.addAll(concreteEventBElement.getAllContained(removeClass, true));
		}
		for (EObject eObject : removeList){
			if (eObject != null){
				EStructuralFeature feature = eObject.eContainingFeature();
				EObject parent = eObject.eContainer();
				if (parent != null && feature!= null && parent.eClass().getEStructuralFeatures().contains(feature)) {
					if (feature.isMany()){
						((EList<EObject>) parent.eGet(feature)).remove(eObject);
					}else{
						parent.eUnset(feature);
					}
				}
			}
		}
	}

	/*
	 * This sets up the references in the new refined model according to the 
	 * settings in the referenceMap
	 */
	@SuppressWarnings("unchecked")
	private void copyReferences(URI abstractUri, EventBElement concreteEventBElement, Copier copier, URI concreteResourceURI, EventBNamedCommentedComponentElement concreteComponent, String concreteComponentName) {
		// Set up references in the new concrete model  (note that copier.copyReferences() does not work for this)
		//get all the content of the root Element including itself
		EList<EObject> contents = concreteEventBElement.getAllContained(CorePackage.Literals.EVENT_BELEMENT, true);
		contents.add(concreteEventBElement);
		// iterate through the contents looking for references corresponding to those declared in the referencemap
		// and copy them in the appropriate way according to multiplicity and the refencemap.
		for (EObject concreteElement : contents){
			if (concreteElement instanceof EventBElement){
				EReference referenceFeature;
				for (Entry<EReference, RefHandling> referenceEntry : referencemap.entrySet()){
					referenceFeature = referenceEntry.getKey(); 
					if (referenceFeature.getEContainingClass().isSuperTypeOf(concreteElement.eClass())){
						EObject abstractElement = getKeyByValue(copier, concreteElement);
						//NOTE: *** Cannot use the concrete elements to create URIs because their parentage is not complete ***
						if (referenceFeature.isMany()){
							for (EObject abstractReferencedElement : (EList<EObject>)(getKeyByValue(copier, concreteElement)).eGet(referenceFeature)){		
								EObject newValue = getNewReferenceValue(
										abstractUri,
										(EventBObject)abstractElement,
										(EventBObject)abstractReferencedElement,
										concreteResourceURI, concreteComponent, concreteComponentName,
										referenceEntry.getValue(), copier);
								if (newValue!=null){
									((EList<EObject>)concreteElement.eGet(referenceFeature)).add(newValue);
								}
							}
						}else{
							if (referenceFeature.getEType() instanceof EClass){
								EObject newValue = getNewReferenceValue(
										abstractUri,
										(EventBObject)abstractElement,
										(EventBObject)abstractElement.eGet(referenceFeature,false),
										concreteResourceURI, concreteComponent, concreteComponentName,
										referenceEntry.getValue(), copier);
								if (newValue!=null){
									concreteElement.eSet(referenceFeature, newValue);
								}
							}
						}
					}
				}
			}
		}
	}


	/**
	 * 
	 * The abstractReferencesElement must be contained in a resource
	 * 
	 * @param abstractElement
	 * @param abstractReferencedElement
	 * @param handling
	 * @return
	 */
	private EObject getNewReferenceValue(URI abstractElementUri, EventBObject abstractElement, EventBObject abstractReferencedElement, 
			URI concreteResourceURI, EventBNamedCommentedComponentElement concreteComponent, String concreteComponentName,
			RefHandling handling, Copier copier) {
		
		EClass eclass = null;
		URI uri = null;
		if (abstractReferencedElement!=null && abstractReferencedElement.eIsProxy()){
			abstractReferencedElement = (EventBObject) EcoreUtil.resolve(abstractReferencedElement, abstractElement.eResource());
		}
		switch (handling){
		case CHAIN:
			uri = abstractElementUri;
			eclass = abstractElement.eClass();	
			break;
		case EQUIV:
			if (abstractReferencedElement instanceof EObject){
				uri = EcoreUtil.getURI((EObject)abstractReferencedElement);
				URI abstractResourceURI = ((EObject)abstractElement).eResource().getURI();
				if (uri !=null && uri.path().equals(abstractResourceURI.path())){ //equiv only works for intra-machine refs
					EventBObject abstractComponent = ((EventBObject) abstractReferencedElement).getContaining(CorePackage.Literals.EVENT_BNAMED_COMMENTED_COMPONENT_ELEMENT);
					String abstractComponentName = "null";
					if (abstractComponent instanceof EventBNamed){
						abstractComponentName = ((EventBNamed)abstractComponent).getName();
					}else{
						//FIXME: not sure if this is necessary.. or works.. better to make sure abstractReferencedElement is contained?
						abstractComponentName = abstractElementUri.fragment();
						abstractComponentName = abstractComponentName.substring(abstractComponentName.lastIndexOf("::")+2);
						abstractComponentName = abstractComponentName.substring(0,abstractComponentName.indexOf("."));
					}
					if (copier.containsKey(abstractReferencedElement)){
						String id = EcoreUtil.getID((EObject)abstractReferencedElement).replaceAll("::"+abstractComponentName+"\\.", "::"+concreteComponentName+".");
						uri = concreteResourceURI.appendFragment(id);
						eclass = ((EObject)abstractReferencedElement).eClass();						
					}else if (concreteComponent!=null){
						EObject target = getEquivalentObject(concreteComponent, abstractReferencedElement);
						if (target != null){
							uri = EcoreUtil.getURI(target);
							eclass = target.eClass();
						}
					}
					break;
				}
			//when equiv is not possible default to copy
			}
		case COPY:
			if (abstractReferencedElement instanceof EObject){
				uri = EcoreUtil.getURI((EObject)abstractReferencedElement);
				uri = uri==null? uri : uri.appendFragment(EcoreUtil.getID((EObject)abstractReferencedElement));
				eclass = ((EObject)abstractReferencedElement).eClass();
			}
			break;
		case DROP:
			uri = null;
			eclass = null;
		}
		return (uri==null || eclass==null)? null : EMFCoreUtil.createProxy(eclass, uri);
	}


}