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
package org.springframework.ide.vscode.boot.java.utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.springframework.ide.vscode.boot.java.beans.CachedBean;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class SpringIndexerJavaContext {

	private final IJavaProject project;
	private final CompilationUnit cu;
	private final String docURI;
	private final String file;
	private final long lastModified;
	private final AtomicReference<TextDocument> docRef;
	private final String content;
	private final List<CachedSymbol> generatedSymbols;
	private final List<CachedBean> beans;
	private final IProblemCollector getProblemCollector;
	private final List<String> nextPassFiles;
	private final boolean fullAst;
	
	private final Set<String> dependencies = new HashSet<>();
	private final Set<String> scannedTypes = new HashSet<>();
	
	public SpringIndexerJavaContext(
			IJavaProject project, 
			CompilationUnit cu, 
			String docURI, 
			String file, 
			long lastModified,
			AtomicReference<TextDocument> docRef, 
			String content, 
			List<CachedSymbol> generatedSymbols,
			List<CachedBean> beans,
			IProblemCollector problemCollector,
			List<String> nextPassFiles,
			boolean fullAst
	) {
		super();
		this.project = project;
		this.cu = cu;
		this.docURI = docURI;
		this.file = file;
		this.lastModified = lastModified;
		this.docRef = docRef;
		this.content = content;
		this.generatedSymbols = generatedSymbols;
		this.getProblemCollector = problemCollector;
		this.beans = beans;
		this.nextPassFiles = nextPassFiles;
		this.fullAst = fullAst;
	}

	public IJavaProject getProject() {
		return project;
	}

	public CompilationUnit getCu() {
		return cu;
	}

	public String getDocURI() {
		return docURI;
	}

	public String getFile() {
		return file;
	}

	public long getLastModified() {
		return lastModified;
	}

	public AtomicReference<TextDocument> getDocRef() {
		return docRef;
	}

	public String getContent() {
		return content;
	}

	public List<CachedSymbol> getGeneratedSymbols() {
		return generatedSymbols;
	}
	
	public List<CachedBean> getBeans() {
		return beans;
	}
	
	public List<String> getNextPassFiles() {
		return nextPassFiles;
	}

	public Set<String> getDependencies() {
		return dependencies;
	}
	
	public void addDependency(ITypeBinding dependsOn) {
		if (dependsOn != null && dependsOn.isFromSource()) {
			String type = dependsOn.getKey();
		
			if (type != null && !scannedTypes.contains(type)) {
				dependencies.add(type);
			}
		}
	}

	public Set<String> getScannedTypes() {
		return scannedTypes;
	}
	
	public void addScannedType(ITypeBinding scannedType) {
		if (scannedType != null) {
			String type = scannedType.getKey();
			scannedTypes.add(type);
			dependencies.remove(type);
		}
	}

	public IProblemCollector getProblemCollector() {
		return this.getProblemCollector;
	}
	
	public boolean isFullAst() {
		return fullAst;
	}
	
	public void resetDocumentRelatedElements(String docURI) {
		Iterator<CachedBean> beansIterator = beans.iterator();
		while (beansIterator.hasNext()) {
			if (beansIterator.next().getDocURI().equals(docURI)) {
				beansIterator.remove();
			}
		}
		
		Iterator<CachedSymbol> symbolsIterator = generatedSymbols.iterator();
		while (symbolsIterator.hasNext()) {
			if (symbolsIterator.next().getDocURI().equals(docURI)) {
				symbolsIterator.remove();
			}
		}
		
	}
	
}
