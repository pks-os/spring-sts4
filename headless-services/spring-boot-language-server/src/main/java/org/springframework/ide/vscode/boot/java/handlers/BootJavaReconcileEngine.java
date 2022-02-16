/*******************************************************************************
 * Copyright (c) 2016, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.handlers;

import java.net.URI;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.CompilationUnit;
import org.openrewrite.java.tree.JavaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.utils.ORCompilationUnitCache;
import org.springframework.ide.vscode.boot.java.value.Constants;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.util.text.IDocument;

/**
 * @author Martin Lippert
 */
public class BootJavaReconcileEngine implements IReconcileEngine {

	// annotations with SpEL expression params 
	public static final String SPRING_CACHEABLE = "org.springframework.cache.annotation.Cacheable";
	public static final String SPRING_CACHE_EVICT = "org.springframework.cache.annotation.CacheEvict";
	
	public static final String SPRING_EVENT_LISTENER = "org.springframework.context.event.EventListener";
	
	public static final String SPRING_PRE_AUTHORIZE = "org.springframework.security.access.prepost.PreAuthorize";
	public static final String SPRING_PRE_FILTER = "org.springframework.security.access.prepost.PreFilter";
	public static final String SPRING_POST_AUTHORIZE = "org.springframework.security.access.prepost.PostAuthorize";
	public static final String SPRING_POST_FILTER= "org.springframework.security.access.prepost.PostFilter";
	
	public static final String SPRING_CONDITIONAL_ON_EXPRESSION = "org.springframework.boot.autoconfigure.condition.ConditionalOnExpression";
	
	private static final Logger log = LoggerFactory.getLogger(BootJavaReconcileEngine.class);

	private final JavaProjectFinder projectFinder; 
	private final ORCompilationUnitCache compilationUnitCache;
	private final AnnotationParamReconciler[] reconcilers;
	private final SpelExpressionReconciler spelExpressionReconciler;
	
	public BootJavaReconcileEngine(ORCompilationUnitCache compilationUnitCache, JavaProjectFinder projectFinder) {
		this.compilationUnitCache = compilationUnitCache;
		this.projectFinder = projectFinder;
		
		this.spelExpressionReconciler = new SpelExpressionReconciler();
		
		this.reconcilers = new AnnotationParamReconciler[] {

				new AnnotationParamReconciler(Constants.SPRING_VALUE, null, "#{", "}", spelExpressionReconciler),
				new AnnotationParamReconciler(Constants.SPRING_VALUE, "value", "#{", "}", spelExpressionReconciler),

				new AnnotationParamReconciler(SPRING_CACHEABLE, "key", "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_CACHEABLE, "condition", "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_CACHEABLE, "unless", "", "", spelExpressionReconciler),

				new AnnotationParamReconciler(SPRING_CACHE_EVICT, "key", "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_CACHE_EVICT, "condition", "", "", spelExpressionReconciler),

				new AnnotationParamReconciler(SPRING_EVENT_LISTENER, "condition", "", "", spelExpressionReconciler),
				
				new AnnotationParamReconciler(SPRING_PRE_AUTHORIZE, null, "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_PRE_AUTHORIZE, "value", "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_PRE_FILTER, null, "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_PRE_FILTER, "value", "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_POST_AUTHORIZE, null, "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_POST_AUTHORIZE, "value", "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_POST_FILTER, null, "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_POST_FILTER, "value", "", "", spelExpressionReconciler),

				new AnnotationParamReconciler(SPRING_CONDITIONAL_ON_EXPRESSION, null, "", "", spelExpressionReconciler),
				new AnnotationParamReconciler(SPRING_CONDITIONAL_ON_EXPRESSION, "value", "", "", spelExpressionReconciler)
		};
	}

	public void setSpelExpressionSyntaxValidationEnabled(boolean spelExpressionValidationEnabled) {
		this.spelExpressionReconciler.setEnabled(spelExpressionValidationEnabled);
	}

	@Override
	public void reconcile(final IDocument doc, final IProblemCollector problemCollector) {
		IJavaProject project = projectFinder.find(new TextDocumentIdentifier(doc.getUri())).orElse(null);
		URI uri = URI.create(doc.getUri());
		
		if (project != null) {
			
			try {
				problemCollector.beginCollecting();
				
				compilationUnitCache.withCompilationUnit(project, uri, cu -> {
					if (cu != null) {
						reconcileAST(cu, problemCollector);
					}
					
					return null;
				});
			}
			finally {
				problemCollector.endCollecting();
			}
		}
	}

	private void reconcileAST(CompilationUnit cu, IProblemCollector problemCollector) {
		
		new JavaIsoVisitor<IProblemCollector>() {
			public Annotation visitAnnotation(Annotation annotation, IProblemCollector p) {
				try {
					if (annotation.getArguments() != null && !annotation.getArguments().isEmpty()) {
						JavaType type = annotation.getType();
						
						if (type != null) {
							for (int i = 0; i < reconcilers.length; i++) {
								reconcilers[i].visit(annotation, problemCollector);
							}
						}
					}
					return super.visitAnnotation(annotation, p);
				} catch (Exception e) {
					// TODO: OR AST - uncomment later to fix in OR
//					log.error("Failed to visit annotation. Fix in OR.", e);
					return annotation;
				}
			};
		}.visitNonNull(cu, problemCollector);
	}
	
}
