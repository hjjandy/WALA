/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.examples.drivers;

import java.io.File;
import java.util.Collection;
import java.util.Properties;

import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.core.tests.slicer.SlicerTest;
import com.ibm.wala.eclipse.util.CancelException;
import com.ibm.wala.ecore.java.scope.EJavaAnalysisScope;
import com.ibm.wala.emf.wrappers.EMFScopeWrapper;
import com.ibm.wala.emf.wrappers.JavaScopeUtil;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.ParamStatement.CallStatementCarrier;
import com.ibm.wala.ipa.slicer.ParamStatement.ValueNumberCarrier;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.graph.NodeDecorator;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.util.warnings.WalaException;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.GVUtil;

/**
 * 
 * This simple example WALA application computes a slice (@see
 * com.ibm.wala.ipa.slicer.Slicer) and fires off ghostview to view a dot-ted
 * representation of the slice.
 * 
 * This is an example program on how to use the slicer.
 * 
 * See the 'GVSlice' launcher included in the 'launchers' directory.
 * 
 * @author sfink
 */
public class GVSlice {

  /**
   * Name of the postscript file generated by dot
   */
  private final static String PS_FILE = "slice.ps";

  /**
   * Usage: GVSlice -appJar [jar file name] -mainClass [main class] -srcCaller
   * [method name] -srcCallee [method name] -dd [data dependence options] -cd
   * [control dependence options] -dir [forward|backward]
   * 
   * <ul>
   * <li> "jar file name" should be something like
   * "c:/temp/testdata/java_cup.jar"
   * <li> "main class" should beshould be something like
   * "c:/temp/testdata/java_cup.jar"
   * <li> "method name" should be the name of a method. This takes a slice from
   * the statement that calls "srcCallee" from "srcCaller"
   * <li> "data dependence options" can be one of "-full", "-no_base_ptrs",
   * "-no_base_no_heap", "-no_heap", "-no_base_no_heap_no_cast", or "-none".
   * 
   * @throws CancelException
   * @throws IllegalArgumentException
   * 
   * @see com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions
   *      <li> "control dependence options" can be "-full" or "-none"
   *      <li> the -dir argument tells whether to compute a forwards or
   *      backwards slice.
   *      </ul>
   * 
   */
  public static void main(String[] args) throws WalaException, IllegalArgumentException, CancelException {
    run(args);
  }

  /**
   * see main(), above, for command-line arguments
   * 
   * @throws CancelException
   * @throws IllegalArgumentException
   */
  public static Process run(String[] args) throws WalaException, IllegalArgumentException, CancelException {
    // parse the command-line into a Properties object
    Properties p = CommandLine.parse(args);
    // validate that the command-line has the expected format
    validateCommandLine(p);

    // run the applications
    return run(p.getProperty("appJar"), p.getProperty("mainClass"), p.getProperty("srcCaller"), p.getProperty("srcCallee"),
        goBackward(p), GVSDG.getDataDependenceOptions(p), GVSDG.getControlDependenceOptions(p));
  }

  /**
   * Should the slice be a backwards slice?
   */
  private static boolean goBackward(Properties p) {
    return !p.getProperty("dir", "backward").equals("forward");
  }

  /**
   * Compute a slice from a call statements, dot it, and fire off ghostview to
   * visualize the result
   * 
   * @param appJar
   *            should be something like "c:/temp/testdata/java_cup.jar"
   * @param mainClass
   *            should be something like "c:/temp/testdata/java_cup.jar"
   * @param srcCaller
   *            name of the method containing the statement of interest
   * @param srcCallee
   *            name of the method called by the statement of interest
   * @param goBackward
   *            do a backward slice?
   * @param dOptions
   *            options controlling data dependence
   * @param cOptions
   *            options controlling control dependence
   * @return a Process running ghostview to visualize the dot'ted representation
   *         of the slice
   * @throws CancelException
   * @throws IllegalArgumentException
   */
  public static Process run(String appJar, String mainClass, String srcCaller, String srcCallee, boolean goBackward,
      DataDependenceOptions dOptions, ControlDependenceOptions cOptions) throws IllegalArgumentException, CancelException {
    try {
      // create an analysis scope representing the appJar as a J2SE application
      EJavaAnalysisScope escope = JavaScopeUtil.makeAnalysisScope(appJar, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
      EMFScopeWrapper scope = EMFScopeWrapper.generateScope(escope);

      // build a class hierarchy, call graph, and system dependence graph
      ClassHierarchy cha = ClassHierarchy.make(scope);
      Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha, mainClass);
      AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
      CallGraphBuilder builder = Util.makeVanillaZeroOneCFABuilder(options, new AnalysisCache(), cha, scope);
      // CallGraphBuilder builder = Util.makeZeroOneCFABuilder(options, new
      // AnalysisCache(), cha, scope);
      CallGraph cg = builder.makeCallGraph(options);
      SDG sdg = new SDG(cg, builder.getPointerAnalysis(), dOptions, cOptions);

      // find the call statement of interest
      CGNode callerNode = SlicerTest.findMethod(cg, srcCaller);
      Statement s = SlicerTest.findCallTo(callerNode, srcCallee);
      System.err.println("Statement: " + s);

      // compute the slice as a collection of statements
      Collection<Statement> slice = null;
      if (goBackward) {
        slice = Slicer.computeBackwardSlice(s, cg, builder.getPointerAnalysis(), dOptions, cOptions);
      } else {
        // for forward slices ... we actually slice from the return value of
        // calls.
        s = getReturnStatementForCall(s);
        slice = Slicer.computeForwardSlice(s, cg, builder.getPointerAnalysis(), dOptions, cOptions);
      }
      SlicerTest.dumpSlice(slice);

      // create a view of the SDG restricted to nodes in the slice
      Graph<Statement> g = pruneSDG(sdg, slice);

      sanityCheck(slice, g);

      // load Properties from standard WALA and the WALA examples project
      Properties p = null;
      try {
        p = WalaExamplesProperties.loadProperties();
        p.putAll(WalaProperties.loadProperties());
      } catch (WalaException e) {
        e.printStackTrace();
        Assertions.UNREACHABLE();
      }
      // create a dot representation.
      String psFile = p.getProperty(WalaProperties.OUTPUT_DIR) + File.separatorChar + PS_FILE;
      String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
      DotUtil.dotify(g, makeNodeDecorator(), GVTypeHierarchy.DOT_FILE, psFile, dotExe);

      // fire off ghostview
      String gvExe = p.getProperty(WalaExamplesProperties.GHOSTVIEW_EXE);
      return GVUtil.launchGV(psFile, gvExe);

    } catch (WalaException e) {
      // something bad happened.
      e.printStackTrace();
      return null;
    }
  }

  /**
   * check that g is a well-formed graph, and that it contains exactly the
   * number of nodes in the slice
   */
  private static void sanityCheck(Collection<Statement> slice, Graph<Statement> g) {
    try {
      GraphIntegrity.check(g);
    } catch (UnsoundGraphException e1) {
      e1.printStackTrace();
      Assertions.UNREACHABLE();
    }
    Assertions.productionAssertion(g.getNumberOfNodes() == slice.size(), "panic " + g.getNumberOfNodes() + " " + slice.size());
  }

  /**
   * If s is a call statement, return the statement representing the normal
   * return from s
   */
  public static Statement getReturnStatementForCall(Statement s) {
    if (s.getKind() == Kind.NORMAL) {
      SSAInstruction st = ((NormalStatement) s).getInstruction();
      if (st instanceof SSAInvokeInstruction) {
        return new ParamStatement.NormalReturnCaller(s.getNode(), (SSAInvokeInstruction) st);
      } else {
        return s;
      }
    } else {
      return s;
    }
  }

  /**
   * return a view of the sdg restricted to the statements in the slice
   */
  public static Graph<Statement> pruneSDG(SDG sdg, final Collection<Statement> slice) {
    Filter<Statement> f = new Filter<Statement>() {
      public boolean accepts(Statement o) {
        return slice.contains(o);
      }
    };
    return GraphSlicer.prune(sdg, f);
  }

  /**
   * @return a NodeDecorator that decorates statements in a slice for a dot-ted
   *         representation
   */
  public static NodeDecorator makeNodeDecorator() {
    return new NodeDecorator() {
      public String getLabel(Object o) throws WalaException {
        Statement s = (Statement) o;
        switch (s.getKind()) {
        case HEAP_PARAM_CALLEE:
        case HEAP_PARAM_CALLER:
        case HEAP_RET_CALLEE:
        case HEAP_RET_CALLER:
          HeapStatement h = (HeapStatement) s;
          return s.getKind() + "\\n" + h.getNode() + "\\n" + h.getLocation();
        case NORMAL:
          NormalStatement n = (NormalStatement) s;
          return n.getInstruction() + "\\n" + n.getNode().getMethod().getSignature();
        case PARAM_CALLEE:
        case PARAM_CALLER:
          if (s instanceof ValueNumberCarrier) {
            ValueNumberCarrier vc = (ValueNumberCarrier) s;
            if (s instanceof CallStatementCarrier) {
              CallStatementCarrier cc = (CallStatementCarrier) s;

              return s.getKind() + " " + vc.getValueNumber() + "\\n" + s.getNode().getMethod().getName() + "\\n"
                  + cc.getCall().getCallSite().getDeclaredTarget().getName();
            } else {
              return s.getKind() + " " + vc.getValueNumber() + "\\n" + s.getNode().getMethod().getName();
            }
          } else {
            if (s instanceof CallStatementCarrier) {
              CallStatementCarrier cc = (CallStatementCarrier) s;
              return s.getKind() + "\\n" + s.getNode() + "\\n" + cc.getCall();
            } else {
              return s.toString();
            }
          }

        case EXC_RET_CALLEE:
        case EXC_RET_CALLER:
        case NORMAL_RET_CALLEE:
        case NORMAL_RET_CALLER:
        case PHI:
        default:
          return s.toString();
        }
      }

    };
  }

  /**
   * Validate that the command-line arguments obey the expected usage.
   * 
   * Usage:
   * <ul>
   * <li> args[0] : "-appJar"
   * <li> args[1] : something like "c:/temp/testdata/java_cup.jar"
   * <li> args[2] : "-mainClass"
   * <li> args[3] : something like "Lslice/TestRecursion" *
   * <li> args[4] : "-srcCallee"
   * <li> args[5] : something like "print" *
   * <li> args[4] : "-srcCaller"
   * <li> args[5] : something like "main"
   * 
   * @throws UnsupportedOperationException
   *             if command-line is malformed.
   */
  static void validateCommandLine(Properties p) {
    if (p.get("appJar") == null) {
      throw new UnsupportedOperationException("expected command-line to include -appJar");
    }
    if (p.get("mainClass") == null) {
      throw new UnsupportedOperationException("expected command-line to include -mainClass");
    }
    if (p.get("srcCallee") == null) {
      throw new UnsupportedOperationException("expected command-line to include -srcCallee");
    }
    if (p.get("srcCaller") == null) {
      throw new UnsupportedOperationException("expected command-line to include -srcCaller");
    }
  }
}