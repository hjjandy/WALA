/*
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.wala.util.graph.impl;

import com.ibm.wala.util.graph.EdgeManager;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;

/** An edge manager that reverses the edges in a graph */
public class InvertingEdgeManager<T> implements EdgeManager<T> {

  private final EdgeManager<T> original;

  public InvertingEdgeManager(EdgeManager<T> original) {
    if (original == null) {
      throw new IllegalArgumentException("original is null");
    }
    this.original = original;
  }

  @Override
  public Iterator<T> getPredNodes(@Nullable T N) throws IllegalArgumentException {
    return original.getSuccNodes(N);
  }

  @Override
  public int getPredNodeCount(T N) throws IllegalArgumentException {
    return original.getSuccNodeCount(N);
  }

  @Override
  public Iterator<T> getSuccNodes(@Nullable T N) throws IllegalArgumentException {
    return original.getPredNodes(N);
  }

  @Override
  public int getSuccNodeCount(T N) throws IllegalArgumentException {
    return original.getPredNodeCount(N);
  }

  @Override
  public void addEdge(T src, T dst) throws IllegalArgumentException {
    original.addEdge(dst, src);
  }

  @Override
  public void removeEdge(T src, T dst) throws IllegalArgumentException {
    original.removeEdge(dst, src);
  }

  @Override
  public boolean hasEdge(@Nullable T src, @Nullable T dst) {
    return original.hasEdge(dst, src);
  }

  @Override
  public void removeAllIncidentEdges(T node) throws IllegalArgumentException {
    original.removeAllIncidentEdges(node);
  }

  @Override
  public void removeIncomingEdges(T node) throws IllegalArgumentException {
    original.removeOutgoingEdges(node);
  }

  @Override
  public void removeOutgoingEdges(T node) throws IllegalArgumentException {
    original.removeIncomingEdges(node);
  }
}
