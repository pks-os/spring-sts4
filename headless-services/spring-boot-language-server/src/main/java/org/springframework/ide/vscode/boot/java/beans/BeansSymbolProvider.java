/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.handlers.AbstractSymbolProvider;
import org.springframework.ide.vscode.boot.java.handlers.EnhancedSymbolInformation;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxRouterSymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.FunctionUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJava.SCAN_PASS;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * @author Martin Lippert
 * @author Kris De Volder
 */
public class BeansSymbolProvider extends AbstractSymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(BeansSymbolProvider.class);

	@Override
	public void addSymbols(Annotation node, ITypeBinding typeBinding, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {
		if (node == null) return;
		
		ASTNode parent = node.getParent();
		if (parent == null || !(parent instanceof MethodDeclaration)) return;
		
		MethodDeclaration method = (MethodDeclaration) parent;
		if (isMethodAbstract(method)) return;

		List<SpringIndexElement> childElements = new ArrayList<>();
		
		boolean isWebfluxRouter = WebfluxRouterSymbolProvider.isWebfluxRouterBean(method);
		
		// for webflux details, we need full method body ASTs
		if (isWebfluxRouter) {
			Block methodBody = method.getBody();
			if ((methodBody == null || methodBody.statements() == null || methodBody.statements().size() == 0)
					&& SCAN_PASS.ONE.equals(context.getPass())) {
				context.getNextPassFiles().add(context.getFile());
				return;
			}
			else {
				WebfluxRouterSymbolProvider.createWebfluxElements(method, context, doc, childElements);
			}
		}
		
		boolean isFunction = isFunctionBean(method);

		ITypeBinding beanType = getBeanType(method);
		String markerString = getAnnotations(method);

		for (Tuple2<String, DocumentRegion> nameAndRegion : getBeanNames(node, doc)) {
			try {
				Location location = new Location(doc.getUri(), doc.toRange(nameAndRegion.getT2()));

				EnhancedSymbolInformation enhancedSymbol = new EnhancedSymbolInformation(
						new WorkspaceSymbol(
								beanLabel(isFunction, nameAndRegion.getT1(), beanType.getName(), "@Bean" + markerString),
								SymbolKind.Interface,
								Either.forLeft(location))
				);

				InjectionPoint[] injectionPoints = ASTUtils.findInjectionPoints(method, doc);
				
				Set<String> supertypes = new HashSet<>();
				ASTUtils.findSupertypes(beanType, supertypes);
				
				Collection<Annotation> annotationsOnMethod = ASTUtils.getAnnotations(method);
				AnnotationMetadata[] annotations = ASTUtils.getAnnotationsMetadata(annotationsOnMethod, doc);
				
				Bean beanDefinition = new Bean(nameAndRegion.getT1(), beanType.getQualifiedName(), location, injectionPoints, supertypes, annotations, false);
				if (childElements.size() > 0) {
					for (SpringIndexElement springIndexElement : childElements) {
						beanDefinition.addChild(springIndexElement);
					}
				}

				context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), enhancedSymbol));
				context.getBeans().add(new CachedBean(context.getDocURI(), beanDefinition));

			} catch (BadLocationException e) {
				log.error("", e);
			}
		}
	}

	@Override
	protected void addSymbolsPass1(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		// this checks function beans that are defined as implementations of Function interfaces
		ITypeBinding functionBean = FunctionUtils.getFunctionBean(typeDeclaration, doc);
		if (functionBean != null) {
			try {
				String beanName = BeanUtils.getBeanName(typeDeclaration);
				ITypeBinding beanType = functionBean;
				Location beanLocation = new Location(doc.getUri(), doc.toRange(ASTUtils.nodeRegion(doc, typeDeclaration.getName())));

				WorkspaceSymbol symbol = new WorkspaceSymbol(
						beanLabel(true, beanName, beanType.getName(), null),
						SymbolKind.Interface,
						Either.forLeft(beanLocation));

				context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(),
						new EnhancedSymbolInformation(symbol)));

				
				ITypeBinding concreteBeanType = typeDeclaration.resolveBinding();
				Set<String> supertypes = new HashSet<>();
				ASTUtils.findSupertypes(concreteBeanType, supertypes);
				
				Collection<Annotation> annotationsOnTypeDeclaration = ASTUtils.getAnnotations(typeDeclaration);
				AnnotationMetadata[] annotations = ASTUtils.getAnnotationsMetadata(annotationsOnTypeDeclaration, doc);

				InjectionPoint[] injectionPoints = ASTUtils.findInjectionPoints(typeDeclaration, doc);

				Bean beanDefinition = new Bean(beanName, concreteBeanType.getQualifiedName(), beanLocation, injectionPoints, supertypes, annotations, false);
				context.getBeans().add(new CachedBean(context.getDocURI(), beanDefinition));

			} catch (BadLocationException e) {
				log.error("", e);
			}
		}
	}

	protected Collection<Tuple2<String, DocumentRegion>> getBeanNames(Annotation node, TextDocument doc) {
		Collection<StringLiteral> beanNameNodes = BeanUtils.getBeanNameLiterals(node);

		if (beanNameNodes != null && !beanNameNodes.isEmpty()) {
			ImmutableList.Builder<Tuple2<String,DocumentRegion>> namesAndRegions = ImmutableList.builder();
			for (StringLiteral nameNode : beanNameNodes) {
				String name = ASTUtils.getLiteralValue(nameNode);
				namesAndRegions.add(Tuples.of(name, ASTUtils.stringRegion(doc, nameNode)));
			}
			return namesAndRegions.build();
		}
		else {
			ASTNode parent = node.getParent();
			if (parent instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration) parent;
				return ImmutableList.of(Tuples.of(
						method.getName().toString(),
						ASTUtils.nameRegion(doc, node)
				));
			}
			return ImmutableList.of();
		}
	}

	public static String beanLabel(boolean isFunctionBean, String beanName, String beanType, String markerString) {
		StringBuilder symbolLabel = new StringBuilder();
		symbolLabel.append('@');
		symbolLabel.append(isFunctionBean ? '>' : '+');
		symbolLabel.append(' ');
		symbolLabel.append('\'');
		symbolLabel.append(beanName);
		symbolLabel.append('\'');

		markerString = markerString != null && markerString.length() > 0 ? " (" + markerString + ") " : " ";
		symbolLabel.append(markerString);

		symbolLabel.append(beanType);
		return symbolLabel.toString();
	}

	protected ITypeBinding getBeanType(MethodDeclaration method) {
		return method.getReturnType2().resolveBinding();
	}

	private boolean isFunctionBean(MethodDeclaration method) {
		String returnType = null;

		if (method.getReturnType2().isParameterizedType()) {
			ParameterizedType paramType = (ParameterizedType) method.getReturnType2();
			Type type = paramType.getType();
			ITypeBinding typeBinding = type.resolveBinding();
			returnType = typeBinding.getBinaryName();
		}
		else {
			returnType = method.getReturnType2().resolveBinding().getQualifiedName();
		}

		return FunctionUtils.FUNCTION_FUNCTION_TYPE.equals(returnType) || FunctionUtils.FUNCTION_CONSUMER_TYPE.equals(returnType)
				|| FunctionUtils.FUNCTION_SUPPLIER_TYPE.equals(returnType);
	}

	private String getAnnotations(MethodDeclaration method) {
		StringBuilder result = new StringBuilder();

		List<?> modifiers = method.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation) {
				Annotation annotation = (Annotation) modifier;
				IAnnotationBinding annotationBinding = annotation.resolveAnnotationBinding();
				String type = annotationBinding.getAnnotationType().getBinaryName();

				if (type != null && !Annotations.BEAN.equals(type)) {
					result.append(' ');
					result.append(annotation.toString());
				}
			}
		}
		return result.toString();
	}
	
	private boolean isMethodAbstract(MethodDeclaration method) {
		List<?> modifiers = method.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Modifier && ((Modifier) modifier).isAbstract()) {
				return true;
			}
		}
		return false;
	}

}
