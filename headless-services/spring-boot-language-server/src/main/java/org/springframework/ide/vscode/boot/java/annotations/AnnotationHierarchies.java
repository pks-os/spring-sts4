/*******************************************************************************
 * Copyright (c) 2017, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.annotations;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.CollectorUtil;

import com.google.common.collect.ImmutableList;

/**
 * Utility class for working with annotation and discovering / understanding their
 * 'inheritance' structure.
 * <p>
 * Provides methods to ask questions about inheritance between annotations.

 * @author Kris De Volder
 */
public abstract class AnnotationHierarchies {
	
	private static final Logger log = LoggerFactory.getLogger(AnnotationHierarchies.class);
	
	// this lock is used to protect multi-threaded access to this helper class
	// due to https://bugs.eclipse.org/bugs/show_bug.cgi?id=571247
	private static final Object lock = new Object();

	public static Collection<ITypeBinding> getDirectSuperAnnotations(ITypeBinding typeBinding) {
		synchronized(lock) {
			try {
				IAnnotationBinding[] annotations = typeBinding.getAnnotations();
				if (annotations != null && annotations.length != 0) {
					ImmutableList.Builder<ITypeBinding> superAnnotations = ImmutableList.builder();
					for (IAnnotationBinding ab : annotations) {
						ITypeBinding sa = ab.getAnnotationType();
						if (sa != null) {
							if (!ignoreAnnotation(sa.getQualifiedName())) {
								superAnnotations.add(sa);
							}
						}
					}
					return superAnnotations.build();
				}
			}
			catch (AbortCompilation e) {
				log.debug("compilation aborted ", e);
				// ignore this, it is most likely caused by broken source code, a broken classpath, or some optional dependencies not being on the classpath
			}
	
			return ImmutableList.of();
		}
	}
	
	public static boolean isSubtypeOf(Annotation annotation, String fqAnnotationTypeName) {
		synchronized(lock) {
			ITypeBinding annotationType = annotation.resolveTypeBinding();
			return hasTransitiveSuperAnnotationType(annotationType, fqAnnotationTypeName);

		}
	}

	public static boolean hasTransitiveSuperAnnotationType(ITypeBinding typeBinding, String annotationType) {
		synchronized(lock) {
			return isMetaAnnotation(typeBinding, annotationType::equals);
		}
	}

	public static Collection<ITypeBinding> getMetaAnnotations(ITypeBinding actualAnnotation, Predicate<String> isKeyAnnotationName) {
		synchronized(lock) {
			Stream<ITypeBinding> allSupers = findTransitiveSupers(actualAnnotation, new HashSet<>())
					.skip(1); //Don't include 'actualAnnotation' itself.
			return allSupers
					.filter(candidate -> isMetaAnnotation(candidate, isKeyAnnotationName))
					.collect(CollectorUtil.toImmutableList());
		}
	}

	public static boolean isMetaAnnotation(ITypeBinding candidate, Predicate<String> isKeyAnnotationName) {
		return findTransitiveSupers(candidate, new HashSet<>())
				.anyMatch(sa -> isKeyAnnotationName.test(sa.getQualifiedName()));
	}
	
	public static Collection<IAnnotationBinding> getDirectSuperAnnotationBindings(IAnnotationBinding annotationBinding) {
		synchronized(lock) {
			try {
				if (annotationBinding.getAnnotationType() != null) {
					IAnnotationBinding[] annotations = annotationBinding.getAnnotationType().getAnnotations();
					if (annotations != null && annotations.length != 0) {
						ImmutableList.Builder<IAnnotationBinding> superAnnotations = ImmutableList.builder();
						for (IAnnotationBinding ab : annotations) {
							ITypeBinding sa = ab.getAnnotationType();
							if (sa != null) {
								if (!ignoreAnnotation(sa.getQualifiedName())) {
									superAnnotations.add(ab);
								}
							}
						}
						return superAnnotations.build();
					}
				}
			}
			catch (AbortCompilation e) {
				log.debug("compilation aborted ", e);
				// ignore this, it is most likely caused by broken source code, a broken classpath, or some optional dependencies not being on the classpath
			}
	
			return ImmutableList.of();
		}
	}
	
	public static Stream<IAnnotationBinding> findTransitiveSuperAnnotationBindings( IAnnotationBinding annotationBinding) {
		return internalFindTransitiveSuperAnnotationBindings(annotationBinding, new HashSet<>());
	}
	
	public static Stream<IAnnotationBinding> internalFindTransitiveSuperAnnotationBindings(IAnnotationBinding annotationBinding, Set<String> seen) {
		synchronized (lock) {
			if (annotationBinding.getAnnotationType() != null) {
				if (seen.add(annotationBinding.getAnnotationType().getQualifiedName())) {
					return Stream.concat(Stream.of(annotationBinding), getDirectSuperAnnotationBindings(annotationBinding)
							.stream()
							.flatMap(superBinding -> internalFindTransitiveSuperAnnotationBindings(superBinding, seen)));
				}
			}
			return Stream.empty();
		}
	}
	
	protected static boolean ignoreAnnotation(String fqname) {
		return fqname.startsWith("java."); //mostly intended to capture java.lang.annotation.* types. But really it should be
		//safe to ignore any type defined by the JRE since it can't possibly be inheriting from a spring annotation.
	}
	
	private static Stream<ITypeBinding> findTransitiveSupers(ITypeBinding typeBinding, Set<String> seen) {
		synchronized(lock) {
			if (typeBinding != null) {
				String qname = typeBinding.getQualifiedName();
				if (seen.add(qname)) {
					return Stream.concat(
							Stream.of(typeBinding),
							getDirectSuperAnnotations(typeBinding).stream().flatMap(superBinding -> findTransitiveSupers(superBinding, seen))
					);
				}
			}
			return Stream.empty();
		}
	}

}
