/*
 * FindBugs - Find Bugs in Java programs
 * Copyright (C) 2003-2007 University of Maryland
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.umd.cs.findbugs.ba.jsr305;

import java.lang.annotation.ElementType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.meta.When;

import edu.umd.cs.findbugs.SystemProperties;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.ba.XClass;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.ba.ch.InheritanceGraphVisitor;
import edu.umd.cs.findbugs.classfile.ClassDescriptor;
import edu.umd.cs.findbugs.classfile.analysis.AnnotatedObject;
import edu.umd.cs.findbugs.classfile.analysis.AnnotationValue;
import edu.umd.cs.findbugs.classfile.analysis.EnumValue;
import edu.umd.cs.findbugs.util.DualKeyHashMap;

/**
 * @author William Pugh
 */
public class TypeQualifierApplications {
	static final boolean DEBUG = SystemProperties.getBoolean("tqa.debug");
	
	static Map<AnnotatedObject, Collection<AnnotationValue>> objectAnnotations = new HashMap<AnnotatedObject, Collection<AnnotationValue>>();
	static DualKeyHashMap<XMethod, Integer, Collection<AnnotationValue>> parameterAnnotations 
	= new DualKeyHashMap<XMethod, Integer, Collection<AnnotationValue>>();
	
	 static Collection<AnnotationValue> getDirectAnnotation(AnnotatedObject m) {
		Collection<AnnotationValue> result = objectAnnotations.get(m);
		if (result != null) return result;
		if (m.getAnnotationDescriptors().isEmpty()) return Collections.emptyList();
		result = TypeQualifierResolver.resolveTypeQualifierNicknames(m.getAnnotations());
		if (result.size() == 0) result = Collections.emptyList();
		objectAnnotations.put(m, result);
		return result;
	}
	 static Collection<AnnotationValue> getDirectAnnotation(XMethod m, int parameter) {
		Collection<AnnotationValue> result = parameterAnnotations.get(m, parameter);
		if (result != null) return result;
		if (m.getParameterAnnotationDescriptors(parameter).isEmpty()) return Collections.emptyList();
		result = TypeQualifierResolver.resolveTypeQualifierNicknames(m.getParameterAnnotations(parameter));
		if (result.size() == 0) result = Collections.emptyList();
		parameterAnnotations.put(m, parameter, result);
		return result;
	}
	
	 static void getApplicableApplications(Map<TypeQualifierValue, When> result, XMethod o, int parameter) {
		 Collection<AnnotationValue> values = getDirectAnnotation(o, parameter);
		 ElementType e = ElementType.PARAMETER;
		 for(AnnotationValue v : values) {
			 Object a = v.getValue("applyTo");
			 if (a instanceof Object[]) {
				 for(Object o2 : (Object[]) a) 
					 if (o2 instanceof EnumValue) { 
						 EnumValue ev = (EnumValue)o2;
						 if (ev.desc.getClassName().equals("java/lang/annotation/ElementType") && e.toString().equals(ev.value))
						    constructTypeQualifierAnnotation(result, v);
					 }
			 } else 
				 constructTypeQualifierAnnotation(result, v);
		 }
	}
	public static void getApplicableApplications(Map<TypeQualifierValue, When> result, AnnotatedObject o, ElementType e) {
		 Collection<AnnotationValue> values = getDirectAnnotation(o);
		 for(AnnotationValue v : values) {
			 Object a = v.getValue("applyTo");
			 if (a instanceof Object[]) {
				 for(Object o2 : (Object[]) a) 
					 if (o2 instanceof EnumValue) { 
						 EnumValue ev = (EnumValue)o2;
						 if (ev.desc.getClassName().equals("java/lang/annotation/ElementType") && e.toString().equals(ev.value))
						    constructTypeQualifierAnnotation(result, v);
					 }
			 } else if (o.getElementType().equals(e))
				 constructTypeQualifierAnnotation(result, v);
		 }
	}
	/**
     * @param v
     * @return
     */
    private static void constructTypeQualifierAnnotation( Map<TypeQualifierValue, When> map, AnnotationValue v) {
    	assert map != null;
    	assert v != null;
	    EnumValue whenValue = (EnumValue) v.getValue("when");
	    When when = whenValue == null ? When.ALWAYS : When.valueOf(whenValue.value);
	    TypeQualifierValue tqv = TypeQualifierValue.getValue(v.getAnnotationClass(), v.getValue("value"));
	    if (whenValue == null) {
	    	tqv.setIsStrict();
	    }
	    map.put(tqv, when);
	    if (DEBUG && whenValue == null) {
	    	System.out.println("When value unspecified for type qualifier value " + tqv);
	    }
    }

	 static void getApplicableScopedApplications(Map<TypeQualifierValue, When> result, AnnotatedObject o, ElementType e) {
		AnnotatedObject outer = o.getContainingScope();
		if (outer != null) 
			getApplicableScopedApplications(result, outer, e);
		getApplicableApplications(result, o, e);
	}
	 static Collection<TypeQualifierAnnotation> getApplicableScopedApplications(AnnotatedObject o, ElementType e) {
		Map<TypeQualifierValue, When> result = new HashMap<TypeQualifierValue, When>();
		getApplicableScopedApplications(result, o, e);
		return  TypeQualifierAnnotation.getValues(result);
	}
		
	 static void getApplicableScopedApplications(Map<TypeQualifierValue, When> result, XMethod o, int parameter) {
		 ElementType e = ElementType.PARAMETER;
		getApplicableScopedApplications(result, o, e);
		getApplicableApplications(result, o, parameter);
	}
	 static Collection<TypeQualifierAnnotation> getApplicableScopedApplications(XMethod o, int parameter) {
		Map<TypeQualifierValue, When> result = new HashMap<TypeQualifierValue, When>();
		ElementType e = ElementType.PARAMETER;
		getApplicableScopedApplications(result, o, e);
		getApplicableApplications(result, o, parameter);
		return TypeQualifierAnnotation.getValues(result);
	}
	
	public static Collection<TypeQualifierAnnotation> getApplicableApplications(AnnotatedObject o) {
		return getApplicableScopedApplications(o, o.getElementType());
	}
	public static Collection<TypeQualifierAnnotation> getApplicableApplications(XMethod o, int parameter) {
		return getApplicableScopedApplications(o, parameter);
	}
	
	/*
	 * XXX: is there a more efficient way to do this?
	 */
	private static TypeQualifierAnnotation findMatchingTypeQualifierAnnotation(
			Collection<TypeQualifierAnnotation> typeQualifierAnnotations,
			TypeQualifierValue typeQualifierValue) {
		for (TypeQualifierAnnotation typeQualifierAnnotation : typeQualifierAnnotations) {
			if (typeQualifierAnnotation.typeQualifier.equals(typeQualifierValue)) {
				return typeQualifierAnnotation;
			}
		}
		return null;
	}
	
	private static class ReturnTypeAnnotationLookupResult extends TypeQualifierAnnotationLookupResult {
		/* (non-Javadoc)
		 * @see edu.umd.cs.findbugs.ba.jsr305.TypeQualifierAnnotationLookupResult#combine(edu.umd.cs.findbugs.ba.jsr305.TypeQualifierAnnotation, edu.umd.cs.findbugs.ba.jsr305.TypeQualifierAnnotation)
		 */
		@Override
		protected TypeQualifierAnnotation combine(TypeQualifierAnnotation a, TypeQualifierAnnotation b) {
			TypeQualifierAnnotation combined = TypeQualifierAnnotation.combineReturnTypeAnnotations(a, b);
			if (combined == null) {
				// XXX: annotations are not compatible.
				// Creating an UNKNOWN annotation is probably fine,
				// since it will prevent the
				// return value annotation from being checked.
				combined = TypeQualifierAnnotation.getValue(a.typeQualifier, When.UNKNOWN);
			}
			return combined;
		}
	}
	
	private static class ReturnTypeAnnotationAccumulator implements InheritanceGraphVisitor {
		private TypeQualifierValue typeQualifierValue;
		private XMethod xmethod;
		private TypeQualifierAnnotationLookupResult result;
		
		public ReturnTypeAnnotationAccumulator(TypeQualifierValue typeQualifierValue, XMethod xmethod) {
			assert !xmethod.isStatic();
			this.typeQualifierValue = typeQualifierValue;
			this.xmethod = xmethod;
			this.result = new ReturnTypeAnnotationLookupResult();
		}

		/* (non-Javadoc)
		 * @see edu.umd.cs.findbugs.ba.ch.InheritanceGraphVisitor#visitClass(edu.umd.cs.findbugs.classfile.ClassDescriptor, edu.umd.cs.findbugs.ba.XClass)
		 */
		public boolean visitClass(ClassDescriptor classDescriptor, XClass xclass) {
			assert xclass != null;
			
			// See if this class has a matching method
			XMethod xm = xclass.findMethod(xmethod.getName(), xmethod.getSignature(), false);
			if (xm == null) {
				// No - end this branch of the search
				return false;
			}
			
			// See if matching method is annotated
			TypeQualifierAnnotation tqa = findMatchingTypeQualifierAnnotation(getApplicableApplications(xm), typeQualifierValue);
			if (tqa == null) {
				// continue search in supertype
				return true;
			} else {
				// This branch of search ends here.
				// Add partial result.
				result.addPartialResult(new TypeQualifierAnnotationLookupResult.PartialResult(xm, tqa));
				return false;
			}
		}

		/* (non-Javadoc)
		 * @see edu.umd.cs.findbugs.ba.ch.InheritanceGraphVisitor#visitEdge(edu.umd.cs.findbugs.classfile.ClassDescriptor, edu.umd.cs.findbugs.ba.XClass, edu.umd.cs.findbugs.classfile.ClassDescriptor, edu.umd.cs.findbugs.ba.XClass)
		 */
		public boolean visitEdge(ClassDescriptor sourceDesc, XClass source, ClassDescriptor targetDesc, XClass target) {
			return (target != null);
		}

	}
	
	public static TypeQualifierAnnotationLookupResult lookupTypeQualifierAnnotation(AnnotatedObject o, TypeQualifierValue typeQualifierValue) {
		// TODO: if o is an XMethod (and not static), the result should consider annotations on supertype methods
		TypeQualifierAnnotationLookupResult result = new TypeQualifierAnnotationLookupResult();
		TypeQualifierAnnotation tqa = findMatchingTypeQualifierAnnotation(getApplicableApplications(o), typeQualifierValue);
		if (tqa != null) {
			result.addPartialResult(new TypeQualifierAnnotationLookupResult.PartialResult(o, tqa));
		}
		return result;
	}

	public static TypeQualifierAnnotationLookupResult lookupTypeQualifierAnnotation(XMethod o, int parameter, TypeQualifierValue typeQualifierValue) {
		// TODO: result should consider annotations on supertype methods
		TypeQualifierAnnotationLookupResult result = new TypeQualifierAnnotationLookupResult();
		TypeQualifierAnnotation tqa = findMatchingTypeQualifierAnnotation(getApplicableApplications(o, parameter), typeQualifierValue);
		if (tqa != null) {
			result.addPartialResult(new TypeQualifierAnnotationLookupResult.PartialResult(o, tqa));
		}
		return result;
	}

	/**
	 * Get the applicable TypeQualifierAnnotation matching given
	 * TypeQualifierValue for given AnnotatedObject.
	 * Returns null if there is no applicable annotation of the
	 * given TypeQualifierValue for this object.
	 * 
	 * @param o                  an AnnotatedObject
	 * @param typeQualifierValue a TypeQualifierValue 
	 * @return the TypeQualifierAnnotation matching the AnnotatedObject/TypeQualifierValue,
	 *         or null if there is no matching TypeQualifierAnnotation
	 */
	public static @CheckForNull TypeQualifierAnnotation getApplicableApplication(AnnotatedObject o, TypeQualifierValue typeQualifierValue) {
		TypeQualifierAnnotationLookupResult lookupResult = lookupTypeQualifierAnnotation(o, typeQualifierValue);
		return lookupResult.getEffectiveTypeQualifierAnnotation();
	}
	
	/**
	 * Get the applicable TypeQualifierAnnotation matching given
	 * TypeQualifierValue for given method parameter.
	 * Returns null if there is no applicable annotation of the
	 * given TypeQualifierValue for this parameter.
	 * 
	 * @param o                  an XMethod
	 * @param parameter          parameter number (0 for first declared parameter)
	 * @param typeQualifierValue a TypeQualifierValue 
	 * @return the TypeQualifierAnnotation matching the parameter,
	 *         or null if there is no matching TypeQualifierAnnotation
	 */
	public static @CheckForNull TypeQualifierAnnotation getApplicableApplication(XMethod o, int parameter, TypeQualifierValue typeQualifierValue) {
		TypeQualifierAnnotationLookupResult lookupResult = lookupTypeQualifierAnnotation(o, parameter, typeQualifierValue);
		return lookupResult.getEffectiveTypeQualifierAnnotation();
	}
}
