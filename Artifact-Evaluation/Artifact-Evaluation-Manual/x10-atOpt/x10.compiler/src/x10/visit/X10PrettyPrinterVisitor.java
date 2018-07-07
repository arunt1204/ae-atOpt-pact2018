/*
 *  This file is part of the X10 project (http://x10-lang.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  (C) Copyright IBM Corporation 2006-2016.
 */

package x10.visit;

import static x10.visit.X10PrettyPrinterVisitor.iterationCount;
import static x10.visit.X10PrettyPrinterVisitor.lineNo;
import static x10.visit.X10PrettyPrinterVisitor.typeUniqueID;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import polyglot.ast.Allocation_c;
import polyglot.ast.Assert_c;
import polyglot.ast.Assign;
import polyglot.ast.Binary;
import polyglot.ast.Block;
import polyglot.ast.Block_c;
import polyglot.ast.Branch_c;
import polyglot.ast.CanonicalTypeNode;
import polyglot.ast.CanonicalTypeNode_c;
import polyglot.ast.Case_c;
import polyglot.ast.Catch;
import polyglot.ast.Catch_c;
import polyglot.ast.Conditional_c;
import polyglot.ast.ConstructorCall;
import polyglot.ast.Empty_c;
import polyglot.ast.Eval_c;
import polyglot.ast.Expr;
import polyglot.ast.FieldAssign_c;
import polyglot.ast.FieldDecl_c;
import polyglot.ast.Field_c;
import polyglot.ast.FlagsNode_c;
import polyglot.ast.Formal;
import polyglot.ast.Formal_c;
import polyglot.ast.Id_c;
import polyglot.ast.If_c;
import polyglot.ast.Import_c;
import polyglot.ast.IntLit_c;
import polyglot.ast.Labeled_c;
import polyglot.ast.Lit;
import polyglot.ast.Lit_c;
import polyglot.ast.Local;
import polyglot.ast.LocalAssign_c;
import polyglot.ast.Local_c;
import polyglot.ast.Loop_c;
import polyglot.ast.New;
import polyglot.ast.Node;
import polyglot.ast.NodeFactory;
import polyglot.ast.Node_c;
import polyglot.ast.NullLit_c;
import polyglot.ast.PackageNode_c;
import polyglot.ast.Receiver;
import polyglot.ast.Return;
import polyglot.ast.Return_c;
import polyglot.ast.SourceFile;
import polyglot.ast.Special;
import polyglot.ast.Special_c;
import polyglot.ast.Stmt;
import polyglot.ast.StringLit_c;
import polyglot.ast.SwitchBlock_c;
import polyglot.ast.Switch_c;
import polyglot.ast.Throw_c;
import polyglot.ast.TopLevelDecl;
import polyglot.ast.Try_c;
import polyglot.ast.TypeNode;
import polyglot.ast.Unary;
import polyglot.ast.Unary_c;
import polyglot.frontend.Source;
import polyglot.types.ClassDef;
import polyglot.types.ClassType;
import polyglot.types.ConstructorDef;
import polyglot.types.ContainerType;
import polyglot.types.Context;
import polyglot.types.FieldDef;
import polyglot.types.FieldInstance;
import polyglot.types.FieldInstance_c;
import polyglot.types.Flags;
import polyglot.types.JavaArrayType;
import polyglot.types.JavaArrayType_c;
import polyglot.types.LocalInstance;
import polyglot.types.MethodDef;
import polyglot.types.Name;
import polyglot.types.Package;
import polyglot.types.QName;
import polyglot.types.Ref;
import polyglot.types.SemanticException;
import polyglot.types.Type;
import polyglot.types.TypeSystem;
import polyglot.types.Types;
import polyglot.types.VarInstance;
import polyglot.util.CodeWriter;
import polyglot.util.ErrorInfo;
import polyglot.util.InternalCompilerError;
import polyglot.util.Position;
import polyglot.util.StringUtil;
import polyglot.visit.ContextVisitor;
import polyglot.visit.InnerClassRemover;
import polyglot.visit.NodeVisitor;
import polyglot.visit.Translator;
import x10.Configuration;
import x10.ExtensionInfo;
import x10.ast.AssignPropertyCall_c;
import x10.ast.ClosureCall;
import x10.ast.ClosureCall_c;
import x10.ast.Closure_c;
import x10.ast.HasZeroTest_c;
import x10.ast.LocalTypeDef_c;
import x10.ast.OperatorNames;
import x10.ast.ParExpr;
import x10.ast.ParExpr_c;
import x10.ast.PropertyDecl;
import x10.ast.SettableAssign;
import x10.ast.SettableAssign_c;
import x10.ast.StmtExpr_c;
import x10.ast.StmtSeq_c;
import x10.ast.SubtypeTest_c;
import x10.ast.Tuple_c;
import x10.ast.TypeDecl_c;
import x10.ast.TypeParamNode;
import x10.ast.TypeParamNode_c;
import x10.ast.X10Binary_c;
import x10.ast.X10Call;
import x10.ast.X10Call_c;
import x10.ast.X10CanonicalTypeNode;
import x10.ast.X10Cast_c;
import x10.ast.X10ClassBody_c;
import x10.ast.X10ClassDecl_c;
import x10.ast.X10ConstructorCall_c;
import x10.ast.X10ConstructorDecl_c;
import x10.ast.X10Initializer_c;
import x10.ast.X10Instanceof_c;
import x10.ast.X10LocalDecl_c;
import x10.ast.X10MethodDecl_c;
import x10.ast.X10New;
import x10.ast.X10New_c;
import x10.ast.X10ProcedureCall;
import x10.ast.X10Special;
import x10.ast.X10Unary_c;
import x10.emitter.Emitter;
import x10.emitter.Expander;
import x10.emitter.Join;
import x10.emitter.RuntimeTypeExpander;
import x10.emitter.TryCatchExpander;
import x10.emitter.TypeExpander;
import x10.extension.X10Ext;
import x10.types.ConstrainedType;
import x10.types.FunctionType;
import x10.types.MethodInstance;
import x10.types.MethodInstance_c;
import x10.types.ParameterType;
import x10.types.ParameterType.Variance;
import x10.types.X10ClassDef;
import x10.types.X10ClassType;
import x10.types.X10CodeDef;
import x10.types.X10ConstructorDef;
import x10.types.X10ConstructorInstance;
import x10.types.X10FieldDef_c;
import x10.types.X10FieldInstance;
import x10.types.X10FieldInstance_c;
import x10.types.X10MethodDef;
import x10.types.X10ParsedClassType_c;
import x10.types.X10TypeEnv;
import x10.types.constraints.SubtypeConstraint;
import x10.types.constraints.TypeConstraint;
import x10.util.AnnotationUtils;
import x10.util.CollectionFactory;
import x10.util.HierarchyUtils;
import x10.optimizations.atOptimzer.PlaceNode;
import x10.optimizations.atOptimzer.AbstractPlaceTree;
import x10.optimizations.atOptimzer.EdgeRep;
import x10.optimizations.atOptimzer.ObjNode;
import x10.optimizations.atOptimzer.ClassInfo;
import x10.optimizations.atOptimzer.VarWithLineNo;
import x10.optimizations.atOptimzer.ClosureDetails;
import x10.optimizations.atOptimzer.ForClosureObject;
import x10.optimizations.atOptimzer.ForClosureObjectField;
import x10c.ast.X10CBackingArrayAccessAssign_c;
import x10c.ast.X10CBackingArrayAccess_c;
import x10c.ast.X10CBackingArrayNewArray_c;
import x10c.ast.X10CBackingArray_c;
import x10c.types.X10CContext_c;
import x10c.visit.ClosureRemover;
import x10c.visit.InlineHelper;
import x10cpp.visit.ASTQuery;

/**
 * Visitor on the AST nodes that for some X10 nodes triggers the template based
 * dumping mechanism (and for all others just defaults to the normal pretty
 * printing).
 * 
 * @author Christian Grothoff
 * @author Igor Peshansky (template classes)
 * @author Rajkishore Barik 26th Aug 2006 (added loop optimizations)
 * @author vj Refactored Emitter out.
 */
public class X10PrettyPrinterVisitor extends X10DelegatingVisitor {

    public static final String JAVA_LANG_OBJECT = "java.lang.Object";
    public static final String JAVA_IO_SERIALIZABLE = "java.io.Serializable";
    public static final String X10_CORE_REF = "x10.core.Ref";
    public static final String X10_CORE_STRUCT = "x10.core.Struct";
    public static final String X10_CORE_ANY = "x10.core.Any";

    public static final String CONSTRUCTOR_FOR_ZERO_VALUE_DUMMY_PARAM_TYPE = "java.lang.System";
    public static final String CONSTRUCTOR_FOR_ALLOCATION_DUMMY_PARAM_TYPE = "java.lang.System[]";

    public static final int PRINT_TYPE_PARAMS = 1;
    public static final int BOX_PRIMITIVES = 2;
    public static final int NO_VARIANCE = 4;
    public static final int NO_QUALIFIER = 8;

    public static final boolean useSelfDispatch = true;
    // XTENLANG-2993
    public static final boolean generateSpecialDispatcher = true;
    public static final boolean generateSpecialDispatcherNotUse = false;  // TODO to be removed
    public static final boolean supportGenericOverloading = true;
    public static final boolean supportConstructorSplitting = true;
    // XTENLANG-3058
    public static final boolean supportTypeConstraintsWithErasure = true;
    // XTENLANG-3090 (switched back to use java assertion)
    private static final boolean useJavaAssertion = true;
    // XTENLANG-3086
    public static final boolean supportUpperBounds = false;
    // Support numbered parameters for @Native (e.g. @Native("java","#0.toString()")) for backward compatibility.
    public static final boolean supportNumberedParameterForNative = true;
    // Expose existing special dispatcher through special interfaces (e.g. Arithmetic, Bitwise)
    public static final boolean exposeSpecialDispatcherThroughSpecialInterface = false;

    // N.B. should be as short as file name length which is valid on all supported platforms (e.g. NAME_MAX on linux).
    public static final int longestTypeName = 255; // use hash code if type name becomes longer than some threshold.

    public static final String X10_FUN_PACKAGE = "x10.core.fun";
    public static final String X10_FUN_CLASS_NAME_PREFIX = "Fun";
    public static final String X10_VOIDFUN_CLASS_NAME_PREFIX = "VoidFun";
    public static final String X10_FUN_CLASS_PREFIX = X10_FUN_PACKAGE+"."+X10_FUN_CLASS_NAME_PREFIX;
    public static final String X10_VOIDFUN_CLASS_PREFIX = X10_FUN_PACKAGE+"."+X10_VOIDFUN_CLASS_NAME_PREFIX;
    public static final String X10_RTT_TYPE = "x10.rtt.Type";
    public static final String X10_RTT_TYPES = "x10.rtt.Types";
    public static final String X10_RUNTIME_IMPL_JAVA_X10GENERATED = "x10.runtime.impl.java.X10Generated";
    public static final String X10_RUNTIME_IMPL_JAVA_RUNTIME = "x10.runtime.impl.java.Runtime";
    public static final String X10_RUNTIME_IMPL_JAVA_EVALUTILS = "x10.runtime.impl.java.EvalUtils";
    public static final String X10_RUNTIME_IMPL_JAVA_ARRAYUTILS = "x10.runtime.impl.java.ArrayUtils";

    public static final String X10_RUNTIME_IMPL_JAVA_THROWABLEUTILS = "x10.runtime.impl.java.ThrowableUtils";
    public static final String ENSURE_X10_EXCEPTION = "ensureX10Exception";
    
    public static final String JAVA_LANG_THROWABLE = "java.lang.Throwable";
    public static final String JAVA_LANG_ERROR = "java.lang.Error";

    public static final String MAIN_CLASS = "$Main";
    public static final String RTT_NAME = "$RTT";
    public static final String GETRTT_NAME = "$getRTT";
    public static final String GETPARAM_NAME = "$getParam";
    public static final String INITPARAMS_NAME = "$initParams";
    public static final String CONSTRUCTOR_METHOD_NAME = "$init";
    public static String CONSTRUCTOR_METHOD_NAME(ClassDef cd) {
        return InlineHelper.makeSuperBridgeName(cd, Name.make(CONSTRUCTOR_METHOD_NAME)).toString();
    }
    public static final String CONSTRUCTOR_METHOD_NAME_FOR_REFLECTION = "$initForReflection";
    public static final String BOX_METHOD_NAME = "$box";
    public static final String UNBOX_METHOD_NAME = "$unbox";

    private static final QName ASYNC_CLOSURE = QName.make("x10.compiler.AsyncClosure");
    private static final QName REMOTE_INVOCATION = QName.make("x10.compiler.RemoteInvocation");

    private static int nextId_;

    final public CodeWriter w;
    final public Translator tr;
    final public Emitter er;

    final private Configuration config;
    
    /* Nobita code */
    public static int counter = 0;
    public static boolean iteration = false;
    public static String firstClass = "";
    public static boolean nextIteration = false;
    /* Nobita code */
    
    /* Nobita Code */
    //the graph
	//class details
	public static HashMap<String, LinkedList<ClassInfo>> classDetails = new HashMap<String, LinkedList<ClassInfo>>();
	public static int constructorCount = 0;
	public static String constructorParameters = "";
	
	//Object directed graph
	public static HashMap<Integer, HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>>> graphInfo = 
									new HashMap<Integer, HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>>>();
	//external to store only the input structure of a call
	public static HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> graphInfoInitial = 
			new HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>>();
	//Object Details
	public static HashMap<Integer, HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>>> objInfo = new 
			HashMap<Integer, HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>>>();
	
	//to store the points-to information before loop block
	public static Stack<HashMap<String, LinkedList<EdgeRep>>> theLoop = new Stack<HashMap<String, LinkedList<EdgeRep>>>();
	//to store points-to information before if block
	public static Stack<HashMap<String, LinkedList<EdgeRep>>> theIf = new Stack<HashMap<String, LinkedList<EdgeRep>>>();
	//to store points-to information before if-else block
	public static Stack<HashMap<String, LinkedList<EdgeRep>>> theIf1 = new Stack<HashMap<String, LinkedList<EdgeRep>>>();
	
	public static Stack<VarWithLineNo> currClass = new Stack<VarWithLineNo>();
	public static Stack<VarWithLineNo> currMethod = new Stack<VarWithLineNo>();
	public static Stack<VarWithLineNo> currPlace = new Stack<VarWithLineNo>();
	public static boolean ifBlock = false;
	public static boolean theAlter = false;
	
	//line-Number
	public static int lineNo = 0;
	//to store points-to information after every changes in graph [line-nunber]
	public static Stack<HashMap<String, LinkedList<EdgeRep>>> lastGraphInfo = new Stack<HashMap<String, LinkedList<EdgeRep>>>();
	//variable to count the number of iterations
	public static int iterationCount = -1;
	public static int iterationCountSupp = 0;
	public static boolean countSupp = true;
	public static int edgeNumber = 0;
	
	
	//the set
	//all set details
	public  static HashMap<Integer, HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>>> setInfo = new 
			HashMap<Integer, HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>>>();
	//to store set information before the loop block
	public static Stack<HashMap<String, HashSet<String>>> theLoopSet = new Stack<HashMap<String, HashSet<String>>>();
	//to store set information before if block
	public static Stack<HashMap<String, HashMap<String, HashSet<String>>>> theIfSet = new 
			Stack<HashMap<String, HashMap<String, HashSet<String>>>>();
	//to store set information before if-else block
	public static Stack<HashMap<String, HashMap<String, HashSet<String>>>> theIfSetBase = new 
			Stack<HashMap<String, HashMap<String, HashSet<String>>>>();
	
	
	//the place var details
	public static boolean atCall = false;
	public static HashMap<Integer, HashMap<Integer, PlaceNode>> placeTree = new HashMap<Integer, HashMap<Integer, PlaceNode>>();
	public static HashMap<String, ClosureDetails> closureInfo = new HashMap<String, ClosureDetails>();
	
	
	
	//for the second pass
	public static boolean changeConstruct = false;
	public static boolean closureConBody = false;
	public static int zztempNumber = 0;
	
	public static HashMap<Integer, String> lineClosure = new HashMap<Integer, String>();
	public static HashMap<String, HashMap <String, String>> closureVar = new HashMap<String, HashMap <String, String>>();
	public static HashMap<String, HashMap<String, ForClosureObject>> closureObj = new HashMap<String, HashMap<String, ForClosureObject>>();
	public static Stack<String> savedObj = new Stack<String>();
	public static HashMap <String, String> savedObjDet = new HashMap <String, String>();
	
	//to hold package
	public static HashMap <String, String> packageName = new HashMap <String, String>();
	
	//TODO:fix it TemporaryWorkaround
	public static HashMap <String, String> genClass = new HashMap <String, String>();
	
	
	//for the inter-procedural analysis
	public static HashMap<String, LinkedList<EdgeRep>> objParaGraphInfo = new HashMap<String, LinkedList<EdgeRep>>();
	
	//to assist the recursive function
	public static HashMap<String, LinkedList<EdgeRep>> recVarInfo = null;
	
	/* to identify function overloading with signature, hash table to create unique Id's */
	public static HashMap<String, String> typeUniqueID = new HashMap<String, String>();
	
	/*Nobita Code */

    public X10PrettyPrinterVisitor(CodeWriter w, Translator tr) {
        this.w = w;
        this.tr = tr;
        this.er = new Emitter(w, tr);
        this.config = ((ExtensionInfo) tr.job().extensionInfo()).getOptions().x10_config;
    }

    /* to provide a unique name for local variables introduce in the templates */
    private static int getUniqueId_() {
        return nextId_++;
    }

    public static Name getId() {
        return Name.make("$var" + getUniqueId_());
    }

    @Override
    public void visit(Node n) {

        // invoke appropriate visit method for Java backend's specific nodes
        if (n instanceof X10CBackingArray_c) {
            visit((X10CBackingArray_c) n);
            return;
        }
        if (n instanceof X10CBackingArrayAccess_c) {
            visit((X10CBackingArrayAccess_c) n);
            return;
        }
        if (n instanceof X10CBackingArrayAccessAssign_c) {
            visit((X10CBackingArrayAccessAssign_c) n);
            return;
        }
        if (n instanceof X10CBackingArrayNewArray_c) {
            visit((X10CBackingArrayNewArray_c) n);
            return;
        }

        if (n instanceof FlagsNode_c) {
            visit((FlagsNode_c) n);
            return;
        }
        if (n instanceof TypeParamNode_c) {
            visit((TypeParamNode_c) n);
            return;
        }
        if (n instanceof X10Initializer_c) {
            visit((X10Initializer_c) n);
            return;
        }
        tr.job().compiler().errorQueue()
                .enqueue(ErrorInfo.SEMANTIC_ERROR, "Unhandled node type: " + n.getClass(), n.position());

        // Don't call through del; that would be recursive.
        n.translate(w, tr);
    }

    // ///////////////////////////////////////
    // handle Java backend's specific nodes
    // ///////////////////////////////////////

    public void visit(X10CBackingArray_c n) {
        // TODO:CAST
        w.write("(");
        w.write("(");
        JavaArrayType arrayType = (JavaArrayType) n.type();
        er.printType(arrayType.base(), 0);
        w.write("[]");
        w.write(")");
        er.prettyPrint(n.container(), tr);
        w.write(".");
        w.write("value");
        w.write(")");
    }

    public void visit(X10CBackingArrayAccess_c n) {
        // TODO:CAST
        w.write("(");
        w.write("(");
        er.printType(n.type(), PRINT_TYPE_PARAMS);
        w.write(")");

        er.prettyPrint(n.array(), tr);
        w.write("[(int)");
        er.prettyPrint(n.index(), tr);
        w.write("]");
        w.write(")");
    }

    public void visit(X10CBackingArrayAccessAssign_c n) {
        er.prettyPrint(n.array(), tr);
        w.write("[(int)");
        er.prettyPrint(n.index(), tr);
        w.write("]");

        w.write(n.operator().toString());

        boolean closeParen = false;
        if (isPrimitive(n.type()) && isBoxedType(n.right().type())) {
            closeParen = er.printUnboxConversion(n.type());
        }
        er.prettyPrint(n.right(), tr);
        if (closeParen) w.write(")");
    }

    public void visit(X10CBackingArrayNewArray_c n) {
        Type base = ((JavaArrayType) n.type()).base();
        if (base.isParameterType()) {
            w.write("(");
            er.printType(n.type(), 0);
            w.write(")");
            w.write(" ");
            // XTENLANG-3032 following code only works with non-primitives
            /*
            new RuntimeTypeExpander(er, base).expand();
            w.write(".makeArray(");
            w.write(n.dims().get(0).toString());
            w.write(")");
            */
            w.write("new java.lang.Object[");
            er.prettyPrint(n.dims().get(0), tr);
            w.write("]");
            return;
        }
        w.write("new ");
        er.printType(base, 0);
        for (Expr dim : n.dims()) {
            w.write("[");
            er.prettyPrint(dim, tr);
            w.write("]");
        }
        for (int i = 0; i < n.additionalDims(); i++)
            w.write("[]");
    }

    public void visit(FlagsNode_c n) {
        n.translate(w, tr);
    }

    public void visit(TypeParamNode_c n) {
        n.translate(w, tr);
    }

    public void visit(X10Initializer_c n) {
        w.write("static ");
        n.printBlock(n.body(), w, tr);
    }

    // ///////////////////////////////////////
    // handle X10 nodes
    // ///////////////////////////////////////

    @Override
    public void visit(Import_c c) {
        // don't generate any code at all--we should fully qualify all type
        // names
    }

    @Override
    public void visit(PackageNode_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(X10ClassBody_c n) {
        n.translate(w, tr);
    }

    private ClassType X10JavaSerializable_;
    private ClassType X10JavaSerializable() {
        if (X10JavaSerializable_ == null)
            X10JavaSerializable_ = tr.typeSystem().load(Emitter.X10_JAVA_SERIALIZABLE_CLASS);
        return X10JavaSerializable_;
    }

    private boolean canCastToX10JavaSerializable(X10ClassDecl_c n, Type type, Context context) {
        type = Types.baseType(type);

        Type leastUpperBound = null;

        if (type.isParameterType()) {
            if (!supportUpperBounds) return true;
            
            X10ClassDef def = n.classDef();
            Ref<TypeConstraint> tc = def.typeGuard();
            if (tc == null) return true; // no upperbounds
            
            Context c2 = context.pushBlock();
            c2.setName(" ClassGuard for |" + def.name() + "| ");
            c2.setTypeConstraintWithContextTerms(tc);
            X10TypeEnv tenv = tr.typeSystem().env(c2);
            List<Type> upperBounds = tenv.upperBounds(type);
            
            Iterator<Type> it = upperBounds.iterator();
            while (it.hasNext()) {
                Type upperBound = Types.baseType(it.next());
                if (upperBound.isParameterType()) {
                    return canCastToX10JavaSerializable(n, upperBound, context);
                }
                if (upperBound.isClass()) {
                    if (!upperBound.toClass().flags().isInterface()) {
                        if (leastUpperBound == null || upperBound.isSubtype(leastUpperBound, context)) {
                            leastUpperBound = upperBound;
                        }
                    }
                }
            }
        } else if (type.isClass() && !type.toClass().flags().isInterface()) {
            // FIXME uncomment the following requires X10JavaSerializable.class before compiling it.
            // leave it for now because of no immediate problem.
//            leastUpperBound = ftype;
        }

        return leastUpperBound == null || leastUpperBound.isSubtype(X10JavaSerializable(), context);
    }

    @Override
    public void visit(X10ClassDecl_c n) {
        X10ClassDef def = n.classDef();
        X10CContext_c context = (X10CContext_c) tr.context();
        
        /* Nobita Code */
       //System.out.println("This is the name of the class: " + n.name().toString());
       
       /* This code is to update the type(s) with unique id's. So that we for distinguish during fn overloading */
       if (iterationCount == -1) {
    	   typeUniqueID.put("Long", "a1");
    	   typeUniqueID.put("Float", "a2");
    	   typeUniqueID.put("String", "a3");
    	   typeUniqueID.put("FileReader", "a4");
    	   typeUniqueID.put("Printer", "a5");
    	   typeUniqueID.put("Random", "a6");
    	   typeUniqueID.put("FileWriter", "a7");
    	   typeUniqueID.put("Double", "a8");
    	   typeUniqueID.put("Char", "a9");
    	   typeUniqueID.put("PlaceGroup", "a10");
    	   typeUniqueID.put("File", "a11");
    	   typeUniqueID.put("LongRange", "a12");
    	   typeUniqueID.put("Boolean", "a13");
    	   typeUniqueID.put("Rail", "a14");
    	   typeUniqueID.put("Place", "a15");
    	   typeUniqueID.put("Dist", "a16");
    	   typeUniqueID.put("Iterator", "a17");
    	   typeUniqueID.put("Point", "a18");
    	   typeUniqueID.put("Int", "a19");
    	   typeUniqueID.put("Array", "a20");
    	   typeUniqueID.put("DistArray", "a21");
    	   typeUniqueID.put("Region", "a22");
    	   typeUniqueID.put("GlobalRef", "a23");
    	   nextIteration = true;
       }
       
    	if(firstClass.equalsIgnoreCase("")) {
			firstClass = n.name().toString();
			nextIteration = true;
		}
		else {
			
			if(firstClass.equalsIgnoreCase(n.name().toString()) && currClass.size() == 0) {
				lineNo = 0;
				iteration = true;
				
				if (!nextIteration) {
					if (countSupp) {
						countSupp = false;
						iterationCountSupp = iterationCount + 2;
					}
				}
				
				//System.out.println("The value of Next Iteration: " + nextIteration + "iterationCountSupp: " + iterationCountSupp);
				nextIteration = false;
				iterationCount++;
				edgeNumber = 0;
				zztempNumber = 0;
				savedObj = new Stack<String>();
				//System.out.println("Printing the iteration: " + iterationCount);
				//System.out.println("-------------------------------end of one iteration-----------------------------");
				//System.out.println("-------------------------------end of one iteration-----------------------------");
			}
		}
        
    	lineNo++;
    	
    	if (iterationCount == -1) {
    		typeUniqueID.put(n.name().toString(), Integer.toString(lineNo));
    	}
    	
    	VarWithLineNo temp = new VarWithLineNo(n.name().toString(), lineNo);
    	currClass.push(temp);
    	
    	if (!classDetails.containsKey(n.name().toString())) {
    		classDetails.put(n.name().toString(), new LinkedList<ClassInfo>());
    		//update modifier boolean
    	}
    	
    	//the graph
    	if (!graphInfo.containsKey(lineNo)) {
			graphInfo.put(lineNo, new HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>>());
			//update modifier boolean
		}
    	
    	//the object
    	if (!objInfo.containsKey(lineNo)) {
    		objInfo.put(lineNo, new HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>>());
    		//update modifier boolean
    	}
    	
    	//the set
    	if (!setInfo.containsKey(lineNo)) {
			setInfo.put(lineNo, new HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>>());
			//update modifier boolean//update modifier boolean
		}
    	
    	//the place tree
    	if (!placeTree.containsKey(lineNo)) {
    		placeTree.put(lineNo, new HashMap<Integer, PlaceNode>());
    		//update modifier boolean
    	}
    	
    	/* Nobita Code */
        
    	
        // class name and source file name is different. this is the case when StringHelper is defined in String.x10.
    	/* Nobita change code */
    	if (def.isTopLevel() && !def.sourceFile().name().equals(def.name().toString() + ".x10")) {
        	if (!context.containsGeneratedClasses(def)) {
	            context.addGeneratedClasses(def);
	            // not include import
	            SourceFile sf = tr.nodeFactory().SourceFile(n.position(), Collections.<TopLevelDecl> singletonList(n));
	            if (def.package_() != null) {
	                sf = sf.package_(tr.nodeFactory().PackageNode(n.position(), def.package_()));
	            }
	            sf = sf.source(new Source(def.name().toString() + ".x10", n.position().path(), null));
	            //Nobita code 
	            iteration = false;
	            tr.translate(sf);
	            //Nobita code 
	            currClass.pop();
	            if ((currClass.size() > 0) && currClass.peek().name.equalsIgnoreCase(n.name().toString())) {
	            	currClass.pop();
	            }
	            return;
        	} 
        	else { 
        		
        		if (iteration) {
	        		context.addGeneratedClasses(def);
		            // not include import
		            SourceFile sf = tr.nodeFactory().SourceFile(n.position(), Collections.<TopLevelDecl> singletonList(n));
		            if (def.package_() != null) {
		                sf = sf.package_(tr.nodeFactory().PackageNode(n.position(), def.package_()));
		            }
		            sf = sf.source(new Source(def.name().toString() + ".x10", n.position().path(), null));
		            //nobita code
		            iteration = false;
		            tr.translate(sf);
			    iteration = true;
		            //Nobita code 
		            currClass.pop();
	           	    if ((currClass.size() > 0) && currClass.peek().name.equalsIgnoreCase(n.name().toString())) {
	            		currClass.pop();
	           	    }
		            return;
        		}
        	}
        }
        /* Nobita change code */

        // Do not generate code if the class is represented natively.
        if (Emitter.getJavaRep(def) != null) {
            w.write(";");
            w.newline();
            return;
        }

        TypeSystem xts = tr.typeSystem();
    	String mangledDefName = Emitter.mangleToJava(def.name());
    	String mangledDefQName = Emitter.mangleQName(def.asType().fullName()).toString();

        Flags flags = n.flags().flags();

        w.begin(0);
        w.write("@"+X10_RUNTIME_IMPL_JAVA_X10GENERATED);
        w.allowBreak(0);
        if (flags.isInterface()) {
            w.write(flags.clearInterface().clearAbstract().translateJava());
        } else {
            w.write(flags.translateJava());
        }

        if (flags.isInterface()) {
            w.write("interface ");
        } else {
            w.write("class ");
        }

        tr.print(n, n.name(), w);

        List<TypeParamNode> typeParameters = n.typeParameters();
        if (typeParameters.size() > 0) {
            er.printTypeParams(n, context, typeParameters);
            
            /* Nobita code */
             genClass.put(n.name().toString(), null);
             /* Nobita code */
        }

        final TypeNode superClassNode = n.superClass();
        if (!flags.isInterface()) {
            // [DC] all target classes use an extends clause now, even for roots (they extend x.c.Ref)
	        w.write(" extends ");
	        if (flags.isStruct()) {
	            assert superClassNode == null : superClassNode;
	            w.write(X10_CORE_STRUCT);
	        } else {
	            if (superClassNode == null) {
	                // [DC] this is a root
	                w.write(X10_CORE_REF);
	            } else {
	                Type superType = superClassNode.type();
	                er.printType(superType, PRINT_TYPE_PARAMS | BOX_PRIMITIVES | NO_VARIANCE);
	                
	                /* Nobita code - to store the extends class information */
	                
	    			if (iterationCount == 0) {
	    				ClassInfo fieldDetails = new ClassInfo("extends", superClassNode.nameString(), superClassNode.nameString());
	    				LinkedList<ClassInfo> fi = classDetails.get(temp.name);
	    				fi.add(fieldDetails);
	    			}
	                
	                /* Nobita code */
	            }
	        }
        }

        // Filter out x10.lang.Any from the interfaces.
        List<TypeNode> interfaces = new ArrayList<TypeNode>();

        for (TypeNode tn : n.interfaces()) {
            if (!xts.isAny(tn.type())) {
                interfaces.add(tn);
            }
        }
    	// N.B. We cannot represent it with Type node since x10.core.Any is @NativeRep'ed to java.lang.Object instead of to x10.core.Any
        /*
         * Interfaces automatically extend Any if
         * (n.flags().flags().isInterface() && interfaces.isEmpty()) {
         * 
         * X10TypeSystem ts = (X10TypeSystem) tr.typeSystem();
         * interfaces.add(tr.nodeFactory().CanonicalTypeNode(n.position(),
         * ts.Any())); }
         */
        if (!interfaces.isEmpty()) {
            if (flags.isInterface()) {
                w.write(" extends ");
            } else {
                w.write(" implements ");
            }

            List<Type> alreadyPrintedTypes = new ArrayList<Type>();
            for (Iterator<TypeNode> i = interfaces.iterator(); i.hasNext();) {
                TypeNode tn = i.next();
                if (!useSelfDispatch || (useSelfDispatch && !Emitter.alreadyPrinted(alreadyPrintedTypes, tn.type()))) {
                    if (alreadyPrintedTypes.size() != 0) {
                        w.write(", ");
                    }
                    alreadyPrintedTypes.add(tn.type());
                    // the 1st formal parameter of x10.lang.Comparable[T].compareTo(T) must be erased since it implements java.lang.Comparable/*<T>*/.compareTo(Object).
                    // for x10.lang.Point implements java.lang.Comparable/*<x10.lang.Point>*/
                    er.printType(tn.type(), (useSelfDispatch ? 0 : PRINT_TYPE_PARAMS) | BOX_PRIMITIVES | NO_VARIANCE);
                }
            }

            if (!subtypeOfCustomSerializer(def)) {
                if (alreadyPrintedTypes.size() != 0) {
                    w.write(", ");
                }
                w.write(Emitter.X10_JAVA_SERIALIZABLE_CLASS);
            }

        } else if (!def.flags().isInterface() && !(def.asType().toString().equals(CUSTOM_SERIALIZATION))) {
            w.write(" implements " + Emitter.X10_JAVA_SERIALIZABLE_CLASS);
        } else {
        	// make all interfaces extend x10.core.Any
        	// N.B. We cannot represent it with Type node since x10.core.Any is @NativeRep'ed to java.lang.Object instead of to x10.core.Any
        	if (flags.isInterface() && !xts.isAny(def.asType())) {
        	    w.write(" extends " + X10_CORE_ANY);
        	}
        }
        w.unifiedBreak(0);
        w.end();
        w.write("{");
        w.newline(4);
        w.begin(0);

        // print the clone method
        boolean mutable_struct = false;
        try {
            if (def.isStruct() && !def.annotationsMatching(getType("x10.compiler.Mutable")).isEmpty()) {
                mutable_struct = true;
            }
        } catch (SemanticException e) {
        }
        if (mutable_struct) {
            w.write("public ");
            tr.print(n, n.name(), w);
            if (typeParameters.size() > 0) {
                er.printTypeParams(n, context, typeParameters);
            }
            w.write("clone() { try { return (");
            tr.print(n, n.name(), w);
            if (typeParameters.size() > 0) {
                er.printTypeParams(n, context, typeParameters);
            }
            w.write(")super.clone(); } catch (java.lang.CloneNotSupportedException e) { e.printStackTrace() ; return null; } }");
            w.newline();
        }

        // XTENLANG-1102
        er.generateRTTInstance(def);

        // Redirect java serialization to x10 serialization.
        if (!flags.isInterface() && !flags.isAbstract()) {
            w.write("private Object writeReplace() throws java.io.ObjectStreamException {");
            w.newline(4);
            w.begin(0);
            w.write("return new x10.serialization.SerializationProxy(this);");
            w.end();
            w.newline();
            w.writeln("}");
            w.newline();
        }

        // Generate compiler-supported serialization/deserialization code
        if (subtypeOfCustomSerializer(def)) {
            er.generateCustomSerializer(def, n);            
        } else if (subtypeOfUnserializable(def)) {
            w.write("public void " + Emitter.SERIALIZE_METHOD + "(" + Emitter.X10_JAVA_SERIALIZER_CLASS + " $serializer) throws java.io.IOException {");
            w.newline(4);
            w.begin(0);
            w.write("throw new x10.io.NotSerializableException(\"Can't serialize "+def.fullName()+"\");");
            w.end();
            w.newline();
            w.writeln("}");
            w.newline();
        } else {
            if (!def.flags().isInterface()) {
                X10ClassType ct = def.asType();
                ASTQuery query = new ASTQuery(tr);
                // Cannonical ordering of fields by sorting by name.
                FieldInstance[] orderedFields = new FieldInstance[ct.fields().size()];
                for (int i=0; i<ct.fields().size(); i++) {
                    orderedFields[i] = ct.fields().get(i);
                }
                Arrays.sort(orderedFields, new Comparator<FieldInstance>() {
                    public int compare(FieldInstance arg0, FieldInstance arg1) {
                        return arg0.name().toString().compareTo(arg1.name().toString());
                    }});

                //_deserialize_body method
                w.write("public static ");
                //                if (supportUpperBounds)
                if (typeParameters.size() > 0) {
                    er.printTypeParams(n, context, typeParameters);
                    w.write(" ");
                }
                w.write(Emitter.X10_JAVA_SERIALIZABLE_CLASS + " " + Emitter.DESERIALIZE_BODY_METHOD + "(");
                er.printType(def.asType(), PRINT_TYPE_PARAMS | BOX_PRIMITIVES);
                w.write(" $_obj, " + Emitter.X10_JAVA_DESERIALIZER_CLASS + " $deserializer) throws java.io.IOException {");
                w.newline(4);
                w.begin(0);

                if (!config.NO_TRACES && !config.OPTIMIZE) {
                    w.write("if (" + X10_RUNTIME_IMPL_JAVA_RUNTIME + ".TRACE_SER) { ");
                    w.write(X10_RUNTIME_IMPL_JAVA_RUNTIME + ".printTraceMessage(\"X10JavaSerializable: " + Emitter.DESERIALIZE_BODY_METHOD + "() of \" + "  + mangledDefName + ".class + \" calling\"); ");
                    w.writeln("} ");
                }

                er.deserializeSuperClass(superClassNode);

                List<ParameterType> parameterTypes = ct.x10Def().typeParameters();

                // Deserialize type parameters
                for (Iterator<? extends Type> i = parameterTypes.iterator(); i.hasNext(); ) {
                    final Type at = i.next();
                    w.write("$_obj.");
                    er.printType(at, PRINT_TYPE_PARAMS | BOX_PRIMITIVES);
                    w.writeln(" = (" + X10_RTT_TYPE + ") $deserializer.readObject();");
                }

                // Deserialize the instance variables of this class , we do not serialize transient or static variables
                List<FieldInstance> specialTransients = null;
                for (FieldInstance f : orderedFields) {
                    String str;
                    if (f instanceof X10FieldInstance && !query.ifdef(((X10FieldInstance) f).x10Def())) continue;
                    if (f.flags().isStatic() || query.isSyntheticField(f.name().toString()))
                        continue;
                    if (f.flags().isTransient()) {
                        if (!((X10FieldInstance_c)f).annotationsMatching(xts.TransientInitExpr()).isEmpty()) {
                            if (specialTransients == null) {
                                specialTransients = new ArrayList<FieldInstance>();
                            }
                            specialTransients.add(f);
                        }
                        continue;
                    }
                    if (f.type().isParameterType()) {
                        w.write("$_obj." + Emitter.mangleToJava(f.name()) + " = ");
                        if (supportUpperBounds) {
                            w.write("(");
                            er.printType(f.type(), BOX_PRIMITIVES);
                            w.write(") ");
                        }
                        w.writeln("$deserializer.readObject();");
                    } else if ((str = needsCasting(f.type())) != null) {
                        // Want these to be readInteger and so on.....  These do not need a explicit cast cause we are calling special methods
                        w.writeln("$_obj." + Emitter.mangleToJava(f.name()) + " = $deserializer.read" + str + "();");
                    } else if (xts.isJavaArray(f.type())) {
                        w.write("$_obj." + Emitter.mangleToJava(f.name()) + " = ");
                        w.write("(");
                        er.printType(f.type(), BOX_PRIMITIVES);
                        w.write(") ");
                        w.write("$deserializer.readObject();");
                    } else if (f.type().isArray() && f.type() instanceof JavaArrayType_c && ((JavaArrayType_c)f.type()).base().isParameterType()) {
                        // This is to get the test case XTENLANG_2299 to compile. Hope its a generic fix
                        w.write("$_obj." + Emitter.mangleToJava(f.name()) + " = ");
                        // not needed because readObject takes type parameters
                        //                            w.write("(");
                        //                            er.printType(f.type(), BOX_PRIMITIVES);
                        //                            w.write(") ");                            
                        w.writeln("$deserializer.readObject();");
                    } else {
                        // deserialize the variable and cast it back to the correct type
                        w.write("$_obj." + Emitter.mangleToJava(f.name()) + " = ");
                        // not needed because readObject takes type parameters
                        //                            w.write("(");
                        //                            er.printType(f.type(), BOX_PRIMITIVES);
                        //                            w.write(") ");                            
                        w.writeln("$deserializer.readObject();");
                    }            
                }
                                
                if (specialTransients != null) {
                    w.newline();
                    w.writeln("/* fields with @TransientInitExpr annotations */");
                    for (FieldInstance tf:specialTransients) {
                        Expr initExpr = getInitExpr(((X10FieldInstance_c)tf).annotationsMatching(xts.TransientInitExpr()).get(0));
                        if (initExpr != null) {
                            X10CContext_c ctx = (X10CContext_c) tr.context();
                            w.write("$_obj." + Emitter.mangleToJava(tf.name()) + " = ");
                            String old = ctx.setOverideNameForThis("$_obj");
                            tr.print(n, initExpr, w);
                            ctx.setOverideNameForThis(old);
                            w.writeln(";");
                        }
                    }
                    w.newline();
                } 

                w.write("return $_obj;");
                w.end();
                w.newline();
                w.writeln("}");
                w.newline();

                // _deserializer  method
                w.write("public static " + Emitter.X10_JAVA_SERIALIZABLE_CLASS + " " + Emitter.DESERIALIZER_METHOD + "(" + Emitter.X10_JAVA_DESERIALIZER_CLASS + " $deserializer) throws java.io.IOException {");
                w.newline(4);
                w.begin(0);

                if (def.constructors().size() == 0 || def.flags().isAbstract()) {
                    w.write("return null;");
                } else {
                    if (def.isStruct()) {
                        //TODO Keith get rid of this
                        if (!mangledDefName.equals("PlaceLocalHandle")) {
                            w.write(mangledDefQName + " $_obj = new " + mangledDefQName + "((" + CONSTRUCTOR_FOR_ALLOCATION_DUMMY_PARAM_TYPE + ") null");
                            // N.B. in custom deserializer, initialize type params with null
                            for (ParameterType typeParam : def.typeParameters()) {
                                w.write(", (" + X10_RTT_TYPE + ") null");
                            }
                            w.write(");");
                            w.newline();
                        } else {
                            w.writeln(mangledDefQName + " $_obj = new " + mangledDefQName + "(null, (" + CONSTRUCTOR_FOR_ZERO_VALUE_DUMMY_PARAM_TYPE + ") null);");
                        }
                    } else {
                        if (def.flags().isAbstract()) {
                            w.write(mangledDefQName + " $_obj = (" + mangledDefQName + ") ");
                            // call 1-phase constructor
                            w.write("new " + mangledDefQName);
                            w.writeln("();");
                        } else {
                            w.write(mangledDefQName + " $_obj = new " + mangledDefQName + "(");
                            if (supportConstructorSplitting
                                // XTENLANG-2830
                                /*&& !ConstructorSplitterVisitor.isUnsplittable(Types.baseType(def.asType()))*/
                                && !def.flags().isInterface()) {
                                w.write("(" + CONSTRUCTOR_FOR_ALLOCATION_DUMMY_PARAM_TYPE + ") null");
                                // N.B. in custom deserializer, initialize type params with null
                                for (ParameterType typeParam : def.typeParameters()) {
                                    w.write(", (" + X10_RTT_TYPE + ") null");
                                }
                                w.write(");");
                                w.newline();
                            } else {
                                w.writeln(");");
                            }
                        }
                    }
                    if (!def.isStruct()) {
                        w.writeln("$deserializer.record_reference($_obj);");
                    }
                    w.write("return " + Emitter.DESERIALIZE_BODY_METHOD + "($_obj, $deserializer);");
                }
                w.end();
                w.newline();
                w.writeln("}");
                w.newline();

                // _serialize()
                w.write("public void " + Emitter.SERIALIZE_METHOD + "(" + Emitter.X10_JAVA_SERIALIZER_CLASS + " $serializer) throws java.io.IOException {");
                w.newline(4);
                w.begin(0);

                // Serialize the super class first
                er.serializeSuperClass(superClassNode);

                // Serialize any type parameters
                for (Iterator<? extends Type> i = parameterTypes.iterator(); i.hasNext(); ) {
                    final Type at = i.next();
                    w.write("$serializer.write(this.");
                    er.printType(at, PRINT_TYPE_PARAMS | BOX_PRIMITIVES);
                    w.writeln(");");
                }

                // Serialize the public variables of this class , we do not serialize transient or static variables
                for (FieldInstance f : orderedFields) {
                    if (f instanceof X10FieldInstance && !query.ifdef(((X10FieldInstance) f).x10Def())) continue;
                    if (f.flags().isStatic() || query.isSyntheticField(f.name().toString()))
                        continue;
                    if (f.flags().isTransient()) // don't serialize transient fields
                        continue;
                    String fieldName = Emitter.mangleToJava(f.name());
                    w.writeln("$serializer.write(this." + fieldName + ");");
                }
                w.end();
                w.newline();
                w.writeln("}");
                w.newline();
            }
        }

        if (needZeroValueConstructor(def)) {
            er.generateZeroValueConstructor(def, n);
        }

        // print the constructor just for allocation
        if (supportConstructorSplitting
            // XTENLANG-2830
            /*&& !ConstructorSplitterVisitor.isUnsplittable(Types.baseType(def.asType()))*/
            && !def.flags().isInterface()) {
            w.write("// constructor just for allocation");
            w.newline();
            w.write("public " + mangledDefName + "(final " + CONSTRUCTOR_FOR_ALLOCATION_DUMMY_PARAM_TYPE + " $dummy");
            List<String> params = new ArrayList<String>();
            for (ParameterType p : def.typeParameters()) {
                String param = Emitter.mangleParameterType(p);
                w.write(", final " + X10_RTT_TYPE + " " + param);
                params.add(param);
            }
            w.write(") {");
            w.newline(4);
            w.begin(0);
            // call super constructor
            if (flags.isStruct()
                || (superClassNode != null && Emitter.isNativeRepedToJava(superClassNode.type()))
                ) {
                // call default constructor instead of "constructor just for allocation"
            }
            else if (superClassNode != null && superClassNode.type().toClass().isJavaType()) {
                boolean hasDefaultConstructor = false;
                ConstructorDef ctorWithFewestParams = null;
                for (ConstructorDef ctor : superClassNode.type().toClass().def().constructors()) {
                    List<Ref<? extends Type>> formalTypes = ctor.formalTypes();
                    if (formalTypes.size() == 0) {
                        hasDefaultConstructor = true;
                        break;
                    }
                    if (ctorWithFewestParams == null || ctor.formalTypes().size() < ctorWithFewestParams.formalTypes().size()) {
                        ctorWithFewestParams = ctor;
                    }
                }
                if (hasDefaultConstructor) {
                    // call default constructor instead of "constructor just for allocation"
                } else {
                    // XTENLANG-3070
                    // If super class does not have default constructor, call the constructor with the fewest parameters
                    // with all the parameters null or zero.
                    // FIXME This fixes post-compilation error but it may still cause runtime error.
                    assert ctorWithFewestParams != null;
                    w.write("super(");
                    Iterator<Ref<? extends Type>> iter = ctorWithFewestParams.formalTypes().iterator();
                    while (iter.hasNext()) {
                        Type formalType = iter.next().get();
                        if (formalType.isReference()) {
                            w.write("(");
                            er.printType(formalType, 0);
                            w.write(") null");
                        }
                        else if (formalType.isByte() || formalType.isShort()) {
                            w.write("(");
                            er.printType(formalType, 0);
                            w.write(") 0");
                        }
                        else if (formalType.isInt()) {
                            w.write("0");
                        }
                        else if (formalType.isLong()) {
                            w.write("0L");
                        }
                        else if (formalType.isFloat()) {
                            w.write("0.0F");
                        }
                        else if (formalType.isDouble()) {
                            w.write("0.0");
                        }
                        else if (formalType.isChar()) {
                            w.write("'\\0'");
                        }
                        else if (formalType.isBoolean()) {
                            w.write("false");
                        }
                        if (iter.hasNext()) {
                            w.write(", ");
                        }
                    }
                    w.write(");");
                    w.newline();
                }
            }
            else {
                // call "constructor just for allocation"
            	// [DC] if the class doesn't extend anything, don't bother calling super()
                if (def.superType() != null) {
                    w.write("super($dummy");
                    printArgumentsForTypeParamsPreComma(def.superType().get().toClass().typeArguments(), false);
                    w.write(");");
                    w.newline();
                }
            }
            printInitParams(def.asType(), params);
            w.end();
            w.newline();
	    
            w.write("}");
            w.newline();

            w.newline();
        }

        if (useSelfDispatch) {
            er.generateDispatchMethods(def);
        }
        er.generateBridgeMethods(def);

        // print the fields for the type params
        if (typeParameters.size() > 0) {
            w.begin(0);
            if (!flags.isInterface()) {
                for (TypeParamNode tp : typeParameters) {
                    w.write("private ");
                    w.write(X10_RTT_TYPE);
                    // w.write("<"); n.print(tp, w, tr); w.write(">"); // TODO
                    w.write(" ");
                    w.write(Emitter.mangleParameterType(tp));
                    w.write(";");
                    w.newline();
                }
                w.newline();

                w.write("// initializer of type parameters");
                w.newline();
                w.write("public static void ");
                w.write(INITPARAMS_NAME);
                w.write("(final ");
                tr.print(n, n.name(), w);
                /*
                w.write("<");
                boolean first = true;
                for (TypeParamNode tp : typeParameters) {
                    if (first) {
                        first = false;
                    } else {
                        w.write(",");
                    }
                    w.write("?");
                }
                w.write(">");
                */
                w.write(" $this");
                for (TypeParamNode tp : typeParameters) {
                    w.write(", final ");
                    w.write(X10_RTT_TYPE);
                    // w.write("<"); n.print(tp, w, tr); w.write(">"); // TODO
                    w.write(" ");
                    w.write(Emitter.mangleParameterType(tp));
                }
                w.write(") {");
                w.newline(4);
                w.begin(0);
                for (TypeParamNode tp : typeParameters) {
                    w.write("$this.");
                    w.write(Emitter.mangleParameterType(tp));
                    w.write(" = ");
                    w.write(Emitter.mangleParameterType(tp));
                    w.write(";");
                    w.newline();
                }
                w.end();
                w.newline();
                w.write("}");
                w.newline();
            }
            w.end();
        }

        setConstructorIds(def);

        // print synthetic types for parameter mangling
        printExtraTypes(def);

        // print the props
        if (!flags.isInterface()) {
            if (n.properties().size() > 0) {
                w.newline();
                w.writeln("// properties");
                w.begin(0);
                for (PropertyDecl pd : n.properties()) {
                    n.print(pd, w, tr);
                    w.newline();
                }
                w.end();
            }
        }

        w.end();
        w.newline();

        // print the original body
        n.print(n.body(), w, tr);

        w.write("}");
        
        /* Nobita code */
        /* the printer only */
        /*
        VarWithLineNo temp1 = currClass.peek();
        //if ((temp1.name.equalsIgnoreCase("Histogram") || temp1.name.equalsIgnoreCase("test"))) {
	        System.out.println("The class details: " + temp1.name);
	        LinkedList<ClassInfo> ll;
	        ll = classDetails.get(temp1.name);
	        Iterator it = ll.iterator();
			while (it.hasNext())
			{
				ClassInfo ci = (ClassInfo)it.next();
				System.out.println("Classifier: " + ci.classifier  + "   Name: " + ci.name + "   Type: " + ci.type  + " no1: " + ci.classNo
						+ " no2: " + ci.methodNo + " x10Type: " + ci.x10Type + " classNo: " + ci.classNo + " MethodNO: " + ci.methodNo);
				if (ci.uniqueId != null) {
					System.out.println("The unique Id: " + ci.uniqueId);
				}
				
				if (ci.methodPara != null && ci.methodPara.size() > 0) {
					LinkedList<ClassInfo> ll1 = ci.methodPara;
					Iterator it1 = ll1.iterator();
					while (it1.hasNext()) {
						ClassInfo ci1 = (ClassInfo)it1.next();
						System.out.println("The parameter details - Classifier: " + ci1.classifier  + "   Name: " + ci1.name + "   Type: " + ci1.type + " To apply optimization: " + ci1.x10Type);
					}
				}
				
			}
			
			System.out.println("Printing its points to set if any:");
			
			if (objParaGraphInfo != null) {
				Iterator it2 = objParaGraphInfo.entrySet().iterator();
	        	while (it2.hasNext()) {
	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it2.next();
	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
	        		Iterator it1 = edgeIncl.iterator();
	        		while (it1.hasNext())
	        		{
	        			EdgeRep er = (EdgeRep)it1.next();
	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
	        					+ er.fieldName);
	        		}
	        	}
			}
			
			System.out.println("++++++++++++++++++++++++++++++++++++++The end of class+++++++++++++++++++++++++++++");
			System.out.println("");
        //}
         */
		/* the printer only */
        
        
        currClass.pop();
        iteration = true;
		/* Nobita code */
        
        w.newline();
    }

    private Expr getInitExpr(Type at) {
        at = Types.baseType(at);
        if (at instanceof X10ClassType) {
            X10ClassType act = (X10ClassType) at;
            if (0 < act.propertyInitializers().size()) {
                return act.propertyInitializer(0);
            }
        }
        return null;
    }

    // used by custom serializer
    public static String needsCasting(Type type) {
        type = Types.baseType(type);
        if (isPrimitive(type)) {
            String name = type.name().toString();
            if (type.isUnsignedNumeric()) {
                return name.substring(name.lastIndexOf(".") + 1 + 1); // x10.lang.UInt -> Int
            } else {
                return name.substring(name.lastIndexOf(".") + 1); // x10.lang.Int -> Int
            }
        }
        return null;
    }

    private static final String CUSTOM_SERIALIZATION = "x10.io.CustomSerialization";
    public static final String SERIALIZER = "x10.io.Serializer";
    public static final String DESERIALIZER = "x10.io.Deserializer";
    
    private static boolean subtypeOfCustomSerializer(X10ClassDef def) {
        return subtypeOfInterface(def, CUSTOM_SERIALIZATION);
    }

    private static final String UNSERIALIZABLE = "x10.io.Unserializable";
    private static boolean subtypeOfUnserializable(X10ClassDef def) {
        return subtypeOfInterface(def, UNSERIALIZABLE);
    }

    private static boolean subtypeOfInterface(X10ClassDef def, String interfaceName) {
        for (Ref<? extends Type> ref : def.interfaces()) {
            if (interfaceName.equals(ref.get().toString())) {
                return true;
            }
        }
        Ref<? extends Type> ref = def.superType();
        if (ref == null) return false;
        Type type = ref.get();
        if (type instanceof ConstrainedType) {
            type = ((ConstrainedType) type).baseType().get();
        }
        X10ClassDef superDef = ((X10ParsedClassType_c) type).def();
        return subtypeOfInterface(superDef, interfaceName);
    }

    /*
     * (Definition of haszero by Yoav) Formally, the following types haszero: a
     * type that can be null (e.g., Any, closures, but not a struct or
     * Any{self!=null}) Primitive structs (Short,UShort,Byte,UByte, Int, Long,
     * ULong, UInt, Float, Double, Boolean, Char) user defined structs without a
     * constraint and without a class invariant where all fields haszero.
     */
    private static boolean needZeroValueConstructor(X10ClassDef def) {
        if (def.flags().isInterface()) return false;
        if (!def.flags().isStruct()) return false;
        // Note: we don't need zero value constructor for primitive structs
        // because they are cached in x10.rtt.Types class.
        if (isPrimitive(def.asType())) return false;
        // TODO stop generating useless zero value constructor for user-defined
        // struct that does not have zero value
        // user-defined struct does not have zero value if it have a field of
        // type of either
        // 1) type parameter T that does not have haszero constraint
        // 2) any reference (i.e. non-struct) type that has {self != null}
        // consttaint
        // 3) any struct type (including primitive structs) that has any
        // constraint (e.g. Int{self != 0})
        // 4) any user-defined struct that does not have zero value
        return true;
    }

    private static void setConstructorIds(X10ClassDef def) {
        List<ConstructorDef> cds = def.constructors();
        int constructorId = 0;
        for (ConstructorDef cd : cds) {
            X10ConstructorDef xcd = (X10ConstructorDef) cd;
            List<Type> annotations = xcd.annotations();
            List<Ref<? extends Type>> ats = new ArrayList<Ref<? extends Type>>();
            for (Type type : annotations) {
                ats.add(Types.ref(type));
            }
            boolean containsParamOrParameterized = false;
            List<Ref<? extends Type>> formalTypes = xcd.formalTypes();
            for (Ref<? extends Type> ref : formalTypes) {
                Type t = ref.get();
                Type bt = Types.baseType(t);
                // XTENLANG-3259 to avoid post-compilation error with Java constructor with Comparable parameter.
                if (bt.isParameterType() || (hasParams(t) && !Emitter.isNativeRepedToJava(t)) || bt.isUnsignedNumeric()) {
                    containsParamOrParameterized = true;
                    break;
                }
            }
            Type annotationType;
            if (containsParamOrParameterized) {
                annotationType = new ConstructorIdTypeForAnnotation(def).setIndex(constructorId++);
            } else {
                annotationType = new ConstructorIdTypeForAnnotation(def);
            }
            ats.add(Types.ref(annotationType));
            xcd.setDefAnnotations(ats);
        }
    }

    private static boolean hasConstructorIdAnnotation(X10ConstructorDef condef) {
        List<Type> annotations = condef.annotations();
        for (Type an : annotations) {
            if (an instanceof ConstructorIdTypeForAnnotation) {
                return true;
            }
        }
        return false;
    }

    // if it isn't set id or don't have an annotation, return -1
    private static int getConstructorId(X10ConstructorDef condef) {
        if (!hasConstructorIdAnnotation(condef)) {
            ContainerType st = condef.container().get();
            if (st.isClass()) {
                X10ClassDef def = st.toClass().def();
                setConstructorIds(def);
            }
        }
        List<Type> annotations = condef.annotations();
        for (Type an : annotations) {
            if (an instanceof ConstructorIdTypeForAnnotation) {
                return ((ConstructorIdTypeForAnnotation) an).getIndex();
            }
        }
        return -1;
    }

    private Type getType(String name) throws SemanticException {
        return tr.typeSystem().systemResolver().findOne(QName.make(name));
    }

    private boolean isMutableStruct(Type t) {
        TypeSystem ts = tr.typeSystem();
        t = Types.baseType(ts.expandMacros(t));
        if (t.isClass()) {
            X10ClassType ct = t.toClass();
            try {
                if (ct.isX10Struct()) {
                    X10ClassDef cd = ct.def();
                    if (!cd.annotationsMatching(getType("x10.compiler.Mutable")).isEmpty()) {
                        return true;
                    }
                }
            } catch (SemanticException e) {
            }
        }
        return false;
    }

    public static boolean isSplittable(Type type) {
        return supportConstructorSplitting
        && !type.name().toString().startsWith(ClosureRemover.STATIC_NESTED_CLASS_BASE_NAME)
        && !ConstructorSplitterVisitor.isUnsplittable(Types.baseType(type));
    }

    @Override
    public void visit(X10ConstructorDecl_c n) {
    	
    	boolean amIClosure = false;
    	String cName = n.name().toString();
    	//System.out.println("The name of hte constructore: " + cName);
    	
    	if (cName.charAt(0) == '$' && cName.charAt(1) == 'C' && cName.charAt(2) == 'l' && cName.charAt(3) == 'o' && cName.charAt(4) == 's') {
    		amIClosure = true;
    	}
    	/* Nobita code */
    	
    	
        // Checks whether this is the constructor corresponding to CustomSerialization
        boolean isCustomSerializable = false;
        if (n.formals().size() == 1 && DESERIALIZER.equals(n.formals().get(0).type().toString())) {
             isCustomSerializable = true;
        }
        
        /* Nobita code */
        constructorCount = 0;
        lineNo++;
        /* Nobita code */
        
        printCreationMethodDecl(n);
        
        /*Nobita code */
        VarWithLineNo temp1 = currClass.peek();
        LinkedList<ClassInfo> llci1 = classDetails.get(temp1.name);
        if (llci1 != null && iterationCount == 0) {
        	if (constructorCount == 0) {
        		boolean found = false;
        		Iterator it1 = llci1.iterator();
        		while (it1.hasNext()) {
        			ClassInfo ci = (ClassInfo)it1.next();
            		if (ci.classifier.equalsIgnoreCase("constructor") && ci.name.equalsIgnoreCase("0")) {
            			ci.type = "";
            			ci.classNo = temp1.lineNo;
            			ci.methodNo = lineNo;
            			if (ci.methodPara == null) {
	        				ci.methodPara = new LinkedList<ClassInfo>();
	        				String parIndex = ("Obj-Par-this");
	                		ClassInfo ci1 = new ClassInfo(parIndex, temp1.name, "this");
	                		ci.methodPara.addLast(ci1);
	        			}
            			break;
            		}
        		}
        		
        		if (!found) {
            		ClassInfo fieldDetails = new ClassInfo("constructor", constructorParameters, "0");
            		fieldDetails.classNo = temp1.lineNo;
            		fieldDetails.methodNo = lineNo;
            		llci1.add(fieldDetails);
            		fieldDetails.methodPara = new LinkedList<ClassInfo>();
            		String parIndex = ("Obj-Par-this");
            		ClassInfo ci = new ClassInfo(parIndex, temp1.name, "this");
            		fieldDetails.methodPara.addLast(ci);
            	}
        	}
        	else {
        		ClassInfo fieldDetails = new ClassInfo("constructor", "", Integer.toString(constructorCount));
        		fieldDetails.classNo = temp1.lineNo;
        		fieldDetails.methodNo = lineNo;
        		llci1.add(fieldDetails);
        	}
        }
        constructorCount = 0;
        /*Nobita code */
        
        
        X10ClassType type = Types.get(n.constructorDef().container()).toClass();
        if (isSplittable(type)) {
        	
        	/* Nobita code - non-closure constructor uses this path */
        	//this code is for the inter-procedural analysis 
        	if (iterationCount > 0 && (currClass.size() > 0)) {
        		
        		//the method details
        		VarWithLineNo temp2 = new VarWithLineNo("Constructor-"+temp1.name, lineNo);
        		currMethod.push(temp2);
        		
    			//the sets
        		HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        		if (setMethodInfo != null) {
        			if (!setMethodInfo.containsKey(lineNo)) {
    	        		setMethodInfo.put(lineNo, new HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>());
    	        	}
        			
        			//this is for inserting the place line no
    	        	HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(lineNo);
    	        	
    	        	placeInfo.put(lineNo, new HashMap<String, HashMap<String, HashSet<String>>>());
    	        	
    	        	//this is for inserting the sets
    	        	HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(lineNo);
    	        	
    	        	setDetails.put("RS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("CRS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("WS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("MWS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("CWS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("OS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("OVS", new HashMap<String, HashSet<String>>());
        		}
    			
        		//the object
        		HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo2 = objInfo.get(temp1.lineNo);
        		if (methodInfo2 != null) {
        			methodInfo2.put(lineNo, new HashMap<Integer, HashMap<String, ObjNode>>());
        			
        			HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo2.get(lineNo);
            		placeInfo.put(lineNo, new HashMap<String, ObjNode>());
            		
            		//inserting the null object
            		ObjNode nullObj = new ObjNode("Obj-null", "null");
            		
            		//inserting the this object
            		ObjNode thisObj = new ObjNode("obj-this", temp1.name);
            		HashMap<String, ObjNode> objDetails = placeInfo.get(lineNo);
            		objDetails.put("Obj-null", nullObj);
            		objDetails.put("obj-this", thisObj);
            		
            		//the set
                	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo1 = setInfo.get(temp1.lineNo);
                	if (setMethodInfo1 != null) {
                		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfoSet = setMethodInfo.get(lineNo);
                		if (placeInfoSet != null) {
                			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfoSet.get(lineNo);
                			if (setDetails != null) {
                				HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
            					if (!readSet.containsKey("obj-this")) {
            						readSet.put("obj-this", new HashSet<String>());
            						//update modifier boolean
            					}
                        		HashMap<String, HashSet<String>> cumReadSet = setDetails.get("CRS");
                        		if (!cumReadSet.containsKey("obj-this")) {
                        			cumReadSet.put("obj-this", new HashSet<String>());
                        			//update modifier boolean
                        		}
                        		HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
                        		if (!writeSet.containsKey("obj-this")) {
                        			writeSet.put("obj-this", new HashSet<String>());
                        			//update modifier boolean
                        		}
                        		HashMap<String, HashSet<String>> mWriteSet = setDetails.get("MWS");
                        		if (!mWriteSet.containsKey("obj-this")) {
                        			mWriteSet.put("obj-this", new HashSet<String>());
                        			//update modifier boolean
                        		}
                        		HashMap<String, HashSet<String>> cumWriteSet = setDetails.get("CWS");
                        		if (!cumWriteSet.containsKey("obj-this")) {
                        			cumWriteSet.put("obj-this", new HashSet<String>());
                        			//update modifier boolean
                        		}
                        		HashMap<String, HashSet<String>> objectSet = setDetails.get("OS");
                        		if (!objectSet.containsKey("global-os")) {
                        			objectSet.put("global-os", new HashSet<String>()); 
                        			//update modifier boolean
                        		}
                        		HashMap<String, HashSet<String>> objectVarSet = setDetails.get("OVS");
                        		if (!objectVarSet.containsKey("global-ovs")) {
                        			objectVarSet.put("global-ovs", new HashSet<String>());
                        			//update modifier boolean
                        		}
                			}
                		}
                	}
        		}
    			
        		//the graph
            	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo1 = graphInfo.get(temp1.lineNo);
            	if (/*!methodInfo1.containsKey(lineNo)*/ true) {
        			methodInfo1.put(lineNo, new HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>());
        			
        			HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo1.get(lineNo);
        			lineInfo.put(lineNo, new HashMap<String, LinkedList<EdgeRep>>());
        			
        			HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
        			varInfo.put("this", new LinkedList<EdgeRep>());
        			
        			LinkedList<EdgeRep> edgeIncl = varInfo.get("this");
        			EdgeRep edgeInfo = new EdgeRep("P","obj-this");
        			edgeIncl.addLast(edgeInfo);
        			
            		//this is for the object fields of the obj-this
                	LinkedList<ClassInfo> ll = classDetails.get(temp1.name);
                	if (ll != null) {
                		Iterator it = ll.iterator();
                		
                		while (it.hasNext()) {
                			ClassInfo fd = (ClassInfo)it.next();
                			if (fd.classifier.equalsIgnoreCase("field")) {
                        		if((fd != null) && !((fd.type.equalsIgnoreCase("Long")) || (fd.type.equalsIgnoreCase("Float")) || (fd.type.equalsIgnoreCase("String")) || (fd.type.equalsIgnoreCase("FileReader")) || (fd.type.equalsIgnoreCase("Printer")) || (fd.type.equalsIgnoreCase("Random")) || (fd.type.equalsIgnoreCase("FileWriter")) || 
                                		(fd.type.equalsIgnoreCase("Double")) || (fd.type.equalsIgnoreCase("Char")) || (fd.type.equalsIgnoreCase("PlaceGroup")) || (fd.type.equalsIgnoreCase("File")) || (fd.type.equalsIgnoreCase("FailedDynamicCheckException")) || (fd.type.equalsIgnoreCase("FinishState")) || (fd.type.equalsIgnoreCase("LongRange")) ||
                                		(fd.type.equalsIgnoreCase("Boolean")) || (fd.type.equalsIgnoreCase("Rail")) || (fd.type.equalsIgnoreCase("Place")) || (fd.type.equalsIgnoreCase("Dist")) || (fd.type.equalsIgnoreCase("ArrayList")) || (fd.type.equalsIgnoreCase("Iterator")) || (fd.type.equalsIgnoreCase("Point")) || (fd.type.equalsIgnoreCase("Int")) ||
                                		(fd.type.equalsIgnoreCase("Array")) || (fd.type.equalsIgnoreCase("DistArray")) || (fd.type.equalsIgnoreCase("Region")) || (fd.type.equalsIgnoreCase("GlobalRef")))) {
                        			
                        			if(!(varInfo.containsKey("obj-this"))) {
                                		varInfo.put("obj-this", new LinkedList<EdgeRep>());
                                		//update modifier boolean
                                	}
                        			
                        			LinkedList<EdgeRep> edgeInclField = varInfo.get("obj-this");
                        			if (edgeInclField != null) {
                        				EdgeRep edgeInfo1 = new EdgeRep("F","Obj-null",fd.name);
                        				edgeInclField.addLast(edgeInfo1);
                        			}
                        		}
                			}
                		}
                	}
        			
        		}
            	
            	HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo1.get(lineNo);
            	HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
            	lastGraphInfo.push(deepCopy(varInfo));
            	
            	//inserting into currPlace
            	VarWithLineNo temp3 = new VarWithLineNo(("place" + lineNo), lineNo);
            	currPlace.push(temp3);
            	
            	//the place tree
            	HashMap<Integer, PlaceNode> methodInfoPlace = placeTree.get(temp1.lineNo);
            	if (!methodInfoPlace.containsKey(lineNo)) {
            		PlaceNode pn = new PlaceNode(n.name().toString());
            		methodInfoPlace.put(lineNo, pn);
            		//update modifier boolean
            	}
        	}
        	/* Nobita code */
        	
            printConstructorMethodDecl(n, isCustomSerializable);
            
            
            /* Nobita Code */
            // the below code is for the replacing the zero parameter constructor!
            LinkedList<ClassInfo> llci = classDetails.get(temp1.name);
            if (llci != null && iterationCount == 0) {
            	boolean found = false;
            	Iterator it = llci.iterator();
            	while (it.hasNext()) {
            		ClassInfo ci = (ClassInfo)it.next();
            		if (ci.classifier.equalsIgnoreCase("constructor") && ci.name.equalsIgnoreCase("0")) {
            			found = true;
            			break;
            		}
            	}
            	
            	if (!found) {
            		ClassInfo fieldDetails = new ClassInfo("constructor", constructorParameters, "0");
            		llci.add(fieldDetails);
            	}
            	constructorParameters = "";
            }
            
            //add the code here-initial ones will be updated with all the summary
            HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo2 = graphInfo.get(temp1.lineNo); 
            if (methodInfo2 != null && currMethod.size() > 0) {
            	HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo2.get(currMethod.peek().lineNo);
            	if (lineInfo != null && currPlace.size() > 0) {
            		lineInfo.put(currPlace.peek().lineNo, lastGraphInfo.peek());
            	}
            } 
            /* Nobita Code */
            
            //the printer
            /*
        	VarWithLineNo temp2 = null;
        	VarWithLineNo temp3 = null;
        	boolean goThrough = true;
    		if (currMethod.size() > 0) {
    			temp2 = currMethod.peek();
    		} 
    		else {
    			goThrough = false;
    		}
    		if (currPlace.size() > 0) {
    		 temp3 = currPlace.peek();
    		}
    		else {
    			goThrough = false;
    		}
    		System.out.println("The Graph details: ");
        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
        	if (methodInfo != null && goThrough) {
        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
        		if (lineInfo != null) { int lineNo1 = lineNo;
        			HashMap<String, LinkedList<EdgeRep>> varInfo = lastGraphInfo.peek(); lineInfo.put(temp3.lineNo, lastGraphInfo.peek());
        			if (varInfo != null) {
        				System.out.println("For the Method: " + temp2.name);
        				Iterator it = varInfo.entrySet().iterator();
        	        	while (it.hasNext()) {
        	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
        	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
        	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
        	        		Iterator it1 = edgeIncl.iterator();
        	        		while (it1.hasNext())
        	        		{
        	        			EdgeRep er = (EdgeRep)it1.next();
        	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
        	        					+ er.fieldName + " EDGE NAME:" + er.edgeName);
        	        		}
        	        	}
        			}
        		}
        	}
        	System.out.println("-----------------------------------end of constructor method---------------------------------");
        	*/
        	/* the printer code */
        	
            
            /* Nobita code */
            if (currMethod.size() > 0) {
            	currMethod.pop();
            }
            if (currPlace.size() > 0) {
            	currPlace.pop();
            }
            if (lastGraphInfo.size() > 0) {
            	lastGraphInfo.removeAllElements();
            }
            /* Nobita code */
            
            return;
        }

        w.begin(0);

        tr.print(n,
                 tr.nodeFactory().FlagsNode(n.flags().position(),
                                            n.flags().flags().clearPrivate().clearProtected().Public()), w);
        tr.print(n, n.name(), w);

        /* Nobita code */
        List<String> params;
        if (iterationCount > iterationCountSupp && amIClosure) {
        	amIClosure = false;
        	closureConBody = true;
        	//call the function here
        	savedObj = new Stack<String>();
        	params = printConstructorFormals_pp1(n, true);
        	savedObj = new Stack<String>();
        }
        else {
        	params = printConstructorFormals(n, true);
        }
        
        savedObj = new Stack<String>();
        savedObjDet = new HashMap <String, String>();
        /* Nobita code */

        if (n.body() != null) {
            // if (typeAssignments.size() > 0) {
            w.write(" {");
            w.newline(4);
            w.begin(0);
            if (n.body().statements().size() > 0) {
                Stmt firstStmt = getFirstStatement(n);
                if (firstStmt instanceof X10ConstructorCall_c) {
                    X10ConstructorCall_c cc = (X10ConstructorCall_c) firstStmt;
                    // n.printSubStmt(cc, w, tr);
                    printConstructorCallForJavaCtor(cc);
                    w.allowBreak(0);
                    if (cc.kind() == ConstructorCall.THIS) params.clear();
                }
            }
            printInitParams(type, params);
            if (n.body().statements().size() > 0) {
                Stmt firstStmt = getFirstStatement(n);
                if (firstStmt instanceof X10ConstructorCall_c)
                    n.printSubStmt(n.body().statements(n.body().statements().subList(1, n.body().statements().size())),
                                   w, tr);
                // vj: the main body was not being written. Added next two
                // lines.
                else
                    n.printSubStmt(n.body(), w, tr);
            } else
                n.printSubStmt(n.body(), w, tr);
            w.end();
            w.newline();
            w.write("}");
            // } else {
            // n.printSubStmt(n.body(), w, tr);
            // }
        } else {
            w.write(";");
        }
        w.end();
        w.newline();
        
        /* Nobita code */
        closureConBody = false;
        savedObj = new Stack<String>();
        savedObjDet = new HashMap <String, String>();
        /* Nobita code */
    }

    private void printCreationMethodDecl(X10ConstructorDecl_c n) {
        X10ClassType type = Types.get(n.constructorDef().container()).toClass();

        if (type.flags().isAbstract()) {
            return;
        }


        List<ParameterType> typeParameters = type.x10Def().typeParameters();

        boolean isSplittable = isSplittable(type);

        List<Formal> formals = n.formals();

        // N.B. we don't generate 1-phase constructor here, since it will be generated as a normal compilation result of X10 constructor.
        if (isSplittable) {
        w.write("// creation method for java code (1-phase java constructor)");
        w.newline();

        tr.print(n,
                 tr.nodeFactory().FlagsNode(n.flags().position(),
                                            n.flags().flags().clearPrivate().clearProtected().Public()), w);

        // N.B. printing type parameters causes post compilation error for XTENLANG_423 and GenericInstanceof16
        er.printType(type, NO_QUALIFIER);

        printConstructorFormals(n, true);

        boolean isFirst = true;
        for (Ref<? extends Type> _throws : n.constructorDef().throwTypes()) {
            if (isFirst) {
                w.write(" throws ");
                isFirst = false;
            } else {
                w.write(", ");                
            }
            er.printType(_throws.get(), 0);
        }


        w.write(" {");
        w.newline(4);
        w.begin(0);

            w.write("this");
            w.write("((" + CONSTRUCTOR_FOR_ALLOCATION_DUMMY_PARAM_TYPE + ") null");
            printArgumentsForTypeParamsPreComma(typeParameters, false);
            w.write(")");
            
            w.write(";"); w.newline();
            
            w.write(CONSTRUCTOR_METHOD_NAME(type.toClass().def()));
        w.write("(");

        for (int i = 0; i < formals.size(); i++) {
            Formal formal = formals.get(i);
            
            /*nobita code */
            constructorCount++;
            /*nobita code */
            
            if (i != 0) {
                w.write(", ");
            }
            tr.print(n, formal.name(), w);
        }

        printExtraArgments((X10ConstructorInstance) n.constructorDef().asInstance());

        w.write(")");

        w.write(";");

        w.end();
        w.newline();
        w.write("}");
        w.newline();

        }
    }

    private Stmt getFirstStatement(X10ConstructorDecl_c n) {
        Stmt firstStmt = n.body().statements().get(0);
        if (firstStmt instanceof Block) {
            List<Stmt> statements = ((Block) firstStmt).statements();
            if (statements.size() == 1) {
                firstStmt = statements.get(0);
            }
        }
        return firstStmt;
    }

    private void printInitParams(Type type, List<String> params) {
        if (params.size() > 0) {
            er.printType(type, 0);
            w.write(".");
            w.write(INITPARAMS_NAME);
            w.write("(this");
            for (String param : params) {
                w.write(", ");
                w.write(param);
            }
            w.writeln(");");
        }
    }

    private void printConstructorMethodDecl(X10ConstructorDecl_c n, boolean isCustomSerializable) {

        w.newline();
        w.writeln("// constructor for non-virtual call");

        String methodName = null;

        Flags ctorFlags = n.flags().flags().clearPrivate().clearProtected().Public().Final();
        tr.print(n, tr.nodeFactory().FlagsNode(n.flags().position(), ctorFlags), w);

        er.printType(n.constructorDef().container().get(), PRINT_TYPE_PARAMS | NO_VARIANCE);
        w.write(" ");
        String ctorName = CONSTRUCTOR_METHOD_NAME(n.constructorDef().container().get().toClass().def()); 
        w.write(ctorName);

        List<String> params = printConstructorFormals(n, false);

        boolean isFirst = true;
        for (Ref<? extends Type> _throws : n.constructorDef().throwTypes()) {
            if (isFirst) {
                w.write(" throws ");
                isFirst = false;
            } else {
                w.write(", ");                
            }
            er.printType(_throws.get(), 0);
        }

        Block body = n.body();
        if (body != null) {

            body = (Block) body.visit(new NodeVisitor() {
                @Override
                public Node leave(Node parent, Node old, Node n, NodeVisitor v) {
                    if (n instanceof Return) {
                        NodeFactory nf = tr.nodeFactory();
                        return nf.Return(n.position(), nf.This(Position.COMPILER_GENERATED));
                    }
                    return n;
                }
            });

            // if (typeAssignments.size() > 0) {
            w.write(" {");
            w.newline(4);
            w.begin(0);
            // if (body.statements().size() > 0) {
            // if (body.statements().get(0) instanceof X10ConstructorCall_c) {
            // X10ConstructorCall_c cc = (X10ConstructorCall_c)
            // body.statements().get(0);
            // n.printSubStmt(cc, w, tr);
            // w.allowBreak(0);
            // if (cc.kind() == ConstructorCall.THIS) typeAssignments.clear();
            // }
            // }

            // If this is the custom serialization constructor we refractor it out into a new method and call it here
            if (isCustomSerializable) {

                // We cant use the same method name in all classes cause it creates and endless loop cause whn super.init is called it calls back to this method
                methodName = n.returnType().type().fullName().toString().replace(".", "$") + "$" + CONSTRUCTOR_METHOD_NAME_FOR_REFLECTION;
                w.writeln(methodName + "(" + n.formals().get(0).name() + ");");
            } else {
                printConstructorBody(n, body);
            }

            if (body.reachable()) {
                w.newline();
                w.write("return this;");
            }
            w.end();
            w.newline();

            w.write("}");
            // } else {
            // n.printSubStmt(body, w, tr);
            // }
        } else {
            w.write(";");
        }
        w.newline();

        // Refactored method that can be called by reflection
        if (isCustomSerializable) {
            w.newline();
            w.write("public void " + methodName + "(" + DESERIALIZER +  " " + n.formals().get(0).name() + ") {");
            w.newline(4);
            w.begin(0);
            n.printSubStmt(body, w, tr);
            w.end();
            w.newline();
            w.write("}");
            w.newline();
        }

    }

    private void printConstructorBody(X10ConstructorDecl_c n, Block body) {
        if (body.statements().size() > 0) {
            if (body.statements().get(0) instanceof X10ConstructorCall_c)
                n.printSubStmt(body.statements(body.statements()
                /* .subList(1, body.statements().size()) */), w, tr);
            // vj: the main body was not being written. Added next
            // two lines.
            else
                n.printSubStmt(body, w, tr);
        } else
            n.printSubStmt(body, w, tr);
    }

    private List<String> printConstructorFormals(X10ConstructorDecl_c n, boolean forceParams) {
    	w.write("(");

        w.begin(0);

        X10ConstructorDef ci = n.constructorDef();
        X10ClassType ct = Types.get(ci.container()).toClass();
        List<String> params = new ArrayList<String>();

        if (forceParams) {
        for (Iterator<ParameterType> i = ct.x10Def().typeParameters().iterator(); i.hasNext();) {
            ParameterType p = i.next();
            w.write("final ");
            w.write(X10_RTT_TYPE);
            w.write(" ");
            String name = Emitter.mangleParameterType(p);
            w.write(name);
            if (i.hasNext() || n.formals().size() > 0) {
                w.write(", ");
            }
            params.add(name);
        }
        }
        
        /* Nobita code */
        // the below code is for the replacing the zero parameter constructor!
        constructorParameters = "";
        //String var to store the unique pattern 
        String uniqValue = "";
        //methodpara
    	LinkedList<ClassInfo> methodPara = null;
        
    	int parNo = lineNo;
        VarWithLineNo temp1 = currClass.peek();
        LinkedList<ClassInfo> llci1 = classDetails.get(temp1.name);
        ClassInfo ci3 = null;
        if (llci1 != null && iterationCount == 0) {
        		Iterator it1 = llci1.iterator();
        		while (it1.hasNext()) {
        			ClassInfo ci1 = (ClassInfo)it1.next();
            		if (ci1.classifier.equalsIgnoreCase("constructor") && !ci1.name.equalsIgnoreCase("0") && ci1.uniqueId == null) {
            			if (ci1.methodPara == null) {
            				ci1.methodPara = new LinkedList<ClassInfo>();
            				methodPara = ci1.methodPara;
            				parNo++;
            	    		String parIndex = ("Obj-Par-this"+parNo);
            	    		ClassInfo ci2 = new ClassInfo(parIndex, temp1.name, "this");
            	    		methodPara.add(ci2);
            	    		ci3 = ci1;
            			}
            			break;
            		}
        		}
        }
        /* Nobita code */
        
        for (Iterator<Formal> i = n.formals().iterator(); i.hasNext();) {
            Formal f = i.next();
        
            /* Nobita code */
            if (iterationCount == 0) {
            	uniqValue = uniqValue + "|" + typeUniqueID.get(f.type().nameString());
            }
            String fType = f.type().toString();
            if (fType.equalsIgnoreCase("x10.lang.Long")) {
            	constructorParameters = constructorParameters + "0L";
			}
			else if (fType.equalsIgnoreCase("x10.lang.Float")) {
				constructorParameters = constructorParameters + "0.0F";
			}
			else if (fType.equalsIgnoreCase("x10.lang.Double")) {
				constructorParameters = constructorParameters + "0.0";
			}
			else if (fType.equalsIgnoreCase("x10.lang.Char")) {
				constructorParameters = constructorParameters + "'a'";
			}
			else if (fType.equalsIgnoreCase("x10.lang.Boolean")) {
				constructorParameters = constructorParameters + "false";
			}
			else {
				constructorParameters = constructorParameters + "null";
				
				//this code is for the parameter creation;
				String varType = f.type().nameString();
				if(varType != null && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
	            		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
	            		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
	            		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
	            	if (methodPara != null) {
	            		//classifier => ObjPara90; name=> name
	            		parNo++;
	            		String parIndex = ("Obj-Par"+parNo);
	            		ClassInfo ci4 = new ClassInfo(parIndex, varType, f.name().toString());
	            		methodPara.add(ci4);
	            	}
	            }
				
				
			}
            /* Nobita code */
            
            n.print(f, w, tr);
            
            if (i.hasNext()) {
                w.write(", ");
                
                /* Nobita code */
                constructorParameters = constructorParameters + ", ";
                /* Nobita code */
            }
        }
        
        /* Nobita code */
        if (iterationCount == 0 && ci3 != null) {
            	ci3.uniqueId = uniqValue;
        }
        /* Nobita code */

        printExtraFormals(n);

        w.end();
        w.write(")");

        /*
         * if (! n.throwTypes().isEmpty()) { w.allowBreak(6);
         * w.write("throws ");
         * 
         * for (Iterator<TypeNode> i = n.throwTypes().iterator(); i.hasNext(); )
         * { TypeNode tn = (TypeNode) i.next(); er.printType(tn.type(),
         * PRINT_TYPE_PARAMS);
         * 
         * if (i.hasNext()) { w.write(","); w.allowBreak(4); } } }
         */

        return params;
    }

    private void printExtraFormals(X10ConstructorDecl_c n) {
        String dummy = "$dummy";
        int cid = getConstructorId(n.constructorDef());
        if (cid != -1) {
            String extraTypeName = getExtraTypeName(n.constructorDef());
            w.write(", " + extraTypeName + " " + dummy);
            
            /* Nobita code */
            constructorParameters = constructorParameters + ", null";
            /* Nobita code */
        }
    }

    private static String getMangledMethodSuffix(X10ConstructorDef md) {
        ClassType ct = (ClassType) md.container().get();
        List<Ref<? extends Type>> formalTypes = md.formalTypes();
        String methodSuffix = Emitter.getMangledMethodSuffix(ct, formalTypes, true);
        assert methodSuffix.length() > 0;
        return methodSuffix;
    }
    private static String asTypeName(Type containerType, String methodSuffix) {
        X10ClassDef def = containerType.toClass().def();
        String name = def.fullName().toString(); // x10.regionarray.DistArray.LocalState
        Ref<? extends Package> pkg = def.package_();
        if (pkg != null) {
            String packageName = pkg.toString(); // x10.regionarray
            int packageNameLength = packageName.length();
            if (packageNameLength > 0) packageNameLength += 1; // x10.regionarray.
            name = name.substring(packageNameLength); // DistArray.LocalState
        }        
        if (name.length() + 1/*$*/ + methodSuffix.length() + 6/*.class*/> longestTypeName) {
            // if method suffix is too long for file name, replace it with hash code representation of it to avoid post-compilation error. 
            String typeName = "$_" + Integer.toHexString(methodSuffix.hashCode());
//            System.out.println("asTypeName: " + name + ": " + methodSuffix + " -> " + typeName);
            return typeName;            
        } else {
            return methodSuffix;            
        }
    }
    private static String getExtraTypeName(X10ConstructorDef md) {
        assert getConstructorId(md) != -1;
        return asTypeName(md.container().get(), getMangledMethodSuffix(md));
    }
    // should be called after setConstructorIds(def)
    private void printExtraTypes(X10ClassDef def) {
        HashSet<String> extraTypeNames = new HashSet<String>();
        List<ConstructorDef> cds = def.constructors();
        for (ConstructorDef cd : cds) {
            X10ConstructorDef xcd = (X10ConstructorDef) cd;
            int cid = getConstructorId(xcd);
            if (cid != -1) {
                String methodSuffix = getMangledMethodSuffix(xcd);
                String extraTypeName = asTypeName(cd.container().get(), methodSuffix);
                if (!extraTypeNames.contains(extraTypeName)) {
                    extraTypeNames.add(extraTypeName);
                    if (!extraTypeName.equals(methodSuffix)) {
                        w.writeln("// synthetic type for parameter mangling for " + methodSuffix);
                    } else {
                        w.writeln("// synthetic type for parameter mangling");
                    }
                    w.writeln("public static final class " + extraTypeName + " {}");
                }
            }
        }
    }

    private void printConstructorParams(X10ConstructorDecl_c n) {
        w.write("(");

        w.begin(0);

        X10ConstructorDef ci = n.constructorDef();
        X10ClassType ct = Types.get(ci.container()).toClass();

        for (Iterator<Formal> i = n.formals().iterator(); i.hasNext();) {
            Formal f = i.next();
            w.write(f.name().toString());   // TODO mangle?
            if (i.hasNext()) {
                w.write(",");
            }
        }

        printExtraParams(n);

        w.end();
        w.write(")");

        /*
         * if (! n.throwTypes().isEmpty()) { w.allowBreak(6);
         * w.write("throws ");
         * 
         * for (Iterator<TypeNode> i = n.throwTypes().iterator(); i.hasNext(); )
         * { TypeNode tn = (TypeNode) i.next(); er.printType(tn.type(),
         * PRINT_TYPE_PARAMS);
         * 
         * if (i.hasNext()) { w.write(","); w.allowBreak(4); } } }
         */
    }

    private void printExtraParams(X10ConstructorDecl_c n) {
        String dummy = "$dummy";
        int cid = getConstructorId(n.constructorDef());
        if (cid != -1) {
            w.write(", " + dummy);
        }
    }

    // ////////////////////////////////
    // Expr
    // ////////////////////////////////
    @Override
    public void visit(Allocation_c n) {
        Type type = n.type();
        printAllocationCall(type, type.toClass().typeArguments());
    }

    private void printAllocationCall(Type type, List<? extends Type> typeParams) {
        w.write("new ");
        er.printType(type, PRINT_TYPE_PARAMS | NO_VARIANCE);
        w.write("((" + CONSTRUCTOR_FOR_ALLOCATION_DUMMY_PARAM_TYPE + ") null");
        printArgumentsForTypeParamsPreComma(typeParams, false);
        w.write(")");
    }

    @Override
    public void visit(LocalAssign_c n) {
        Local l = n.local();
        TypeSystem ts = tr.typeSystem();
        if (n.operator() == Assign.ASSIGN || isPrimitive(l.type()) || l.type().isString()) {
            tr.print(n, l, w);
            w.write(" ");
            w.write(n.operator().toString());
            w.write(" ");
            er.coerce(n, n.right(), l.type());
            
            /* Nobita code */
            if ((currClass.size() > 0) && (currMethod.size() > 0) && l.type().name().toString() != null) {
            	//getting the class, the place and the method
	        	VarWithLineNo temp1 = currClass.peek();
	        	VarWithLineNo temp2 = currMethod.peek();
	        	VarWithLineNo temp3 = currPlace.peek();
	        	
            	String varType = l.type().name().toString();
            	if((varType != null) && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) ||
                		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
                		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
                		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef"))||
                		(varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")))) {
            		HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
            		if (methodInfo != null) {
            			HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
            			if (lineInfo != null) {
	        				if (lastGraphInfo.size() > 0) {
	        					lineInfo.put(lineNo, lastGraphInfo.pop()); //check for whether back needs to take for second pass also
	        					//update modifier boolean
	        				} 
	        				else {
	        					lineInfo.put(lineNo, new HashMap<String, LinkedList<EdgeRep>>());
		       					//update modifier boolean
	        				}
            				
            				HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
            				if(!(varInfo.containsKey(l.toString()))) {
                        		varInfo.put(l.toString(), new LinkedList<EdgeRep>());
                        		//update modifier boolean
                        	}
            				
            				
            				if (n.right() instanceof Local_c) {
	            				Local_c tempLocal = (Local_c)n.right();
	            				String rhsVar = tempLocal.name().toString();
	            				
	            				varInfo.put(l.toString(), new LinkedList<EdgeRep>());
	            				
	            				LinkedList<EdgeRep> src = varInfo.get(rhsVar);
	                			LinkedList<EdgeRep> dest = varInfo.get(l.toString());	
	                			
	                			if (src != null && dest != null) {
	                				
	                				//for the global ovs
    	            				if (src.size() > 1) {
    	            					HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
    	            					if (setMethodInfo != null) {
    	            						HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
    	            						if (placeInfo != null) {
    	            							HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
    	            							if (setDetails != null) {
    	            								HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
    	            								if (ovSet != null) {
    	            									HashSet<String> set1 = ovSet.get("global-ovs");
    	            									if (set1 != null) {
    	            										set1.add(rhsVar);
    	            									}
    	            								}
    	            							}
    	            						}
    	            					}
    	            				}
	                				
	                				
	                				Iterator it = src.iterator();
	                				
	                				while (it.hasNext()) {
	                					EdgeRep er = (EdgeRep)it.next();
	                					
	                						EdgeRep edgIncl = new EdgeRep("P",er.desName);
	                            			dest.addLast(edgIncl);
	                            			//update modifier boolean
	                				}
	                			}
            				}
            				else if (n.right() instanceof NullLit_c) {
            					varInfo.put(l.toString(), new LinkedList<EdgeRep>());
            					LinkedList<EdgeRep> edgeIncl = varInfo.get(l.toString());
                            	
                            	if (edgeIncl != null) {
                            		Iterator it = edgeIncl.iterator();
                            		boolean found = false;
                            		while (it.hasNext()) {
                            			EdgeRep er = (EdgeRep)it.next();
                            			
                            			if (er.desName.equalsIgnoreCase("Obj-null")) {
                            				found = true;
                            				break;
                            			}
                            		}
                            		if (!found) {
                            			EdgeRep edgeInfo = new EdgeRep("P","Obj-null");
                            			edgeIncl.addLast(edgeInfo);
                            			//update modifier boolean
                            		}
                            	}
            				}
            				else if (n.right() instanceof Field_c) {
            				 	 if (temp2.name.equalsIgnoreCase("operator()")) {
            				 		Field_c tempField = (Field_c)n.right();
            				 		
            				 		String rhsVar = tempField.name().toString();
    	            				
    	            				LinkedList<EdgeRep> src = varInfo.get(rhsVar);
    	                			LinkedList<EdgeRep> dest = varInfo.get(l.toString());	
    	                			
    	                			if (src != null && dest != null) {
    	                				
    	                				//for the global ovs
        	            				if (src.size() > 1) {
        	            					HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	            					if (setMethodInfo != null) {
        	            						HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        	            						if (placeInfo != null) {
        	            							HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
        	            							if (setDetails != null) {
        	            								HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
        	            								if (ovSet != null) {
        	            									HashSet<String> set1 = ovSet.get("global-ovs");
        	            									if (set1 != null) {
        	            										set1.add(rhsVar);
        	            									}
        	            								}
        	            							}
        	            						}
        	            					}
        	            				}
    	                				
    	                				Iterator it = src.iterator();
    	                				
    	                				while (it.hasNext()) {
    	                					EdgeRep er = (EdgeRep)it.next();
    	                					
    	                					Iterator it1 = dest.iterator();
    	                					boolean found = false;
    	                					
    	                					while (it1.hasNext()) {
    	                						EdgeRep er1 = (EdgeRep)it1.next();
    	                						if((er.desName.equalsIgnoreCase(er1.desName))) {
    	    	                					found = true;
    	    	                				}
    	                						
    	                						if(found) {
    	    	                					break;
    	    	                				}
    	                					}
    	                					
    	                					if(!found) {
    	                						EdgeRep edgIncl = new EdgeRep("P",er.desName);
    	                            			dest.addLast(edgIncl);
    	                            			//update modifier boolean
    	    	                			}
    	                				}
    	                			}
            				 		
            				 	 }
            				}
                			//the back up after the changes in points to graph. NOTE:##! check to stop whether it can be done once alone
	        				varInfo = lineInfo.get(lineNo);
	        				if (varInfo != null) {
	        					lastGraphInfo.push(deepCopy(varInfo));
	        				}
            			}
            		}
            	}
            }
            
            /* the printer code */
            /*
        	VarWithLineNo temp1 = currClass.peek();
        	VarWithLineNo temp2 = null;
        	VarWithLineNo temp3 = null;
        	boolean goThrough = true;
        	//if ((temp1.name.equalsIgnoreCase("Histogram") || temp1.name.equalsIgnoreCase("test"))) {
        		if (currMethod.size() > 0) {
        			temp2 = currMethod.peek();
        		} 
        		else {
        			goThrough = false;
        		}
        		if (currPlace.size() > 0) {
        		 temp3 = currPlace.peek();
        		}
        		else {
        			goThrough = false;
        		}
        	//}
        	//System.out.println("I AM PRINTING: " + temp1.lineNo +":"+ temp2.lineNo +":"+ temp3.lineNo +":" + lineNo);
        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
        	if (methodInfo != null && goThrough) {
        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
        		if (lineInfo != null) {
        			HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
        			if (varInfo != null) {
        				System.out.println("GRAPH AFTER LOCAL ASSIGN: " + lineNo);
        				Iterator it = varInfo.entrySet().iterator();
        	        	while (it.hasNext()) {
        	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
        	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
        	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
        	        		Iterator it1 = edgeIncl.iterator();
        	        		while (it1.hasNext())
        	        		{
        	        			EdgeRep er = (EdgeRep)it1.next();
        	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
        	        					+ er.fieldName);
        	        		}
        	        	}
        			}
        		}
        		System.out.println("--------------------------------------------------end of local assign--------------------------------------");
        	}
        	*/
        	/* the printer code */
            /* Nobita code */
            
            
            if (isMutableStruct(l.type())) {
                w.write(".clone()");
            }
        } else {
            Binary.Operator op = n.operator().binaryOperator();
            Name methodName = X10Binary_c.binaryMethodName(op);
            tr.print(n, l, w);
            w.write(" = ");
            tr.print(n, l, w);
            w.write(".");
            w.write(Emitter.mangleToJava(methodName));
            w.write("(");
            tr.print(n, n.right(), w);
            w.write(")");
        }
    }

    // XTENLANG-3287
    private static boolean isFormalTypeErased(X10CodeDef codedef) {
        if (!(codedef instanceof X10MethodDef)) return false;
        X10MethodDef def = (X10MethodDef) codedef;
        if (def.flags().isStatic()) return false;
        String methodName = def.name().toString();
        List<Ref<? extends Type>> formalTypes = def.formalTypes();
        int numFormals = formalTypes.size();

        // the 1st parameter of x10.lang.Comparable[T].compareTo(T)
        if (methodName.equals("compareTo") && numFormals == 1) return true;

        return false;
    }

    @Override
    public void visit(FieldAssign_c n) {
        Type t = n.fieldInstance().type();

        TypeSystem ts = tr.typeSystem();
        if (n.operator() == Assign.ASSIGN || isPrimitive(t) || t.isString()) {
            if (n.target() instanceof TypeNode)
                er.printType(n.target().type(), 0);
            else {
                // XTENLANG-3206, XTENLANG-3208
                if (ts.isParameterType(n.target().type()) || hasParams(n.fieldInstance().container()) || isFormalTypeErased(tr.context().currentCode())) {
                    // TODO:CAST
                    w.write("(");
                    w.write("(");
                    er.printType(n.fieldInstance().container(), PRINT_TYPE_PARAMS);
                    w.write(")");
                    tr.print(n, n.target(), w);
                    w.write(")");
                } else {
                    tr.print(n, n.target(), w);
                }
            }
            w.write(".");
            w.write(Emitter.mangleToJava(n.name().id()));
            w.write(" ");
            w.write(n.operator().toString());
            w.write(" ");
            
            if (iterationCount > iterationCountSupp && closureConBody && n.leftType().toType().name() != null) {
            	String varType = n.leftType().toType().name().toString();
            	if((varType != null) && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
                		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
                		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
                		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
            		
            		VarWithLineNo temp1 = currClass.peek();
            		
            		HashMap <String, String> varClosure = closureVar.get(temp1.name);
            		HashMap<String, ForClosureObject> objClosure = closureObj.get(temp1.name);
            		
            		if (varClosure != null && objClosure != null) {
            			Field_c field = (Field_c)n.left();
            			String targ = "this";
            			String ins = field.fieldInstance().name().toString();
            			
            			String varStatus = "";
            			if (ins.equals("out$$")) {
            				
            				if (varClosure.containsKey("out$$")) {
            					varStatus = varClosure.get(ins);
            				}
            				else {
            					varStatus = varClosure.get("this");
            				}
            			}
            			else {
            				varStatus = varClosure.get(ins);
            			}
            			
            			if (varStatus != null && !varStatus.equalsIgnoreCase("") && !(varStatus.equalsIgnoreCase("multiple"))) {
            				
            				
            				ForClosureObject fco = objClosure.get(varStatus);
            				if (fco != null) {
            					
            					if (!(savedObj.contains(varStatus))) {
            						
            						savedObj.push(varStatus);
            						savedObjDet.put(varStatus, "this."+n.fieldInstance().name().toString());
            						
            						String objIns = "";
            						if (packageName.containsKey(varType)) {
            							objIns = "new "+packageName.get(varType)+"."+(varType)+"((java.lang.System[]) null)."+packageName.get(varType)+"$"+(varType)+"$$init$S(";
            						}
            						else {
            							objIns = "new "+(varType)+"((java.lang.System[]) null)."+(varType)+"$$init$S(";
            						}
	                				
	                				LinkedList<ClassInfo> llci = classDetails.get(varType);
	                				if (llci != null) {
	                					Iterator it = llci.iterator();
	                					while (it.hasNext()) {
	                						ClassInfo ci = (ClassInfo)it.next();
	                	            		if (ci.classifier.equalsIgnoreCase("constructor") && ci.name.equalsIgnoreCase("0")) {
	                	            			objIns = objIns + ci.type;
	                	            			break;
	                	            		}
	                					}
	                				}
	                				
	                				objIns = objIns + ")";
	                				
	                				w.write(objIns);
	            					
	            					LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
	            					if (llFCOF != null && llFCOF.size()>0) {
	            						Iterator it = llFCOF.iterator();
	            						
	            						while (it.hasNext()) {
	            							ForClosureObjectField fcof = (ForClosureObjectField)it.next();
	            							if (fcof.tempStoredName.equalsIgnoreCase("NR")) {
	            								
	            								if (!(savedObj.contains(fcof.fieldObjName))) {
	            									String fieldIns = "";
	            		    						if (packageName.containsKey(fcof.fieldType)) {
	            		    							fieldIns = "new "+packageName.get(fcof.fieldType)+"."+(fcof.fieldType)+"((java.lang.System[]) null)."+packageName.get(fcof.fieldType)+"$"+(fcof.fieldType)+"$$init$S(";
	            		    						}
	            		    						else {
	            		    							fieldIns = "new "+(fcof.fieldType)+"((java.lang.System[]) null)."+(fcof.fieldType)+"$$init$S(";
	            		    						}
		            								
		            								LinkedList<ClassInfo> llci1 = classDetails.get(fcof.fieldType);
		            								if (llci1 != null) {
		            									Iterator it1 = llci1.iterator();
		            									while (it1.hasNext()) {
		            										ClassInfo ci = (ClassInfo)it1.next();
		            	            	            		if (ci.classifier.equalsIgnoreCase("constructor") && ci.name.equalsIgnoreCase("0")) {
		            	            	            			fieldIns = fieldIns + ci.type;
		            	            	            			break;
		            	            	            		}
		            									}
		            								}
		            								
		            								
		            								//add to save obj field
		            								savedObjDet.put(fcof.fieldObjName, targ+"."+ins+"."+fcof.fieldName);
		            								savedObj.push(fcof.fieldObjName);
		            								
		            								fieldIns = fieldIns + ")";
		            								
		            								w.write(";");
		            								w.newline();
		            								
		            								w.write(targ);
		            								w.write(".");
		            								w.write(ins);
		            								w.write(".");
		            								w.write(fcof.fieldName);
		            								w.write(" ");
		            								w.write("=");
		            								w.write(" ");
		            								w.write(fieldIns);
		            								
		            								//call the recursive function here
		            								String lhsName = targ+"."+ins+"."+fcof.fieldName;
		            								prettyPrintCons(objClosure, fcof.fieldObjName, lhsName);
	            								} 
	            								else {
	            									String fieldIns = savedObjDet.get(fcof.fieldObjName);
	            									
	            									w.write(";");
		            								w.newline();
		            								
		            								w.write(targ);
		            								w.write(".");
		            								w.write(ins);
		            								w.write(".");
		            								w.write(fcof.fieldName);
		            								w.write(" ");
		            								w.write("=");
		            								w.write(" ");
		            								w.write(fieldIns);
	            								}
		            								
	            							}
	            							else {
	            								w.write(";");
	            								w.newline();
	            								
	            								w.write(targ);
	            								w.write(".");
	            								w.write(ins);
	            								w.write(".");
	            								w.write(fcof.fieldName);
	            								w.write(" ");
	            								w.write("=");
	            								w.write(" ");
	            								w.write(fcof.tempStoredName);
	            							}
	            						}
	            					}
            					}
            					else {
            						String objIns = savedObjDet.get(varStatus);
            						w.write(objIns);
            					}
	            					
            				}
            				
            			}
            			else {
            				er.coerce(n, n.right(), n.fieldInstance().type());
            			}	
            		}
            		else {
            			er.coerce(n, n.right(), n.fieldInstance().type());
            		}
            	}
            	else {
            		er.coerce(n, n.right(), n.fieldInstance().type());
            	}
            }
            else {
            	er.coerce(n, n.right(), n.fieldInstance().type());
            }

            
            /* Nobita code */
            if ((currClass.size() > 0) && (currMethod.size() > 0) && n.leftType().toType().name().toString() != null) {
            	
            	//getting the class, the place and the method
	        	VarWithLineNo temp1 = currClass.peek();
	        	VarWithLineNo temp2 = currMethod.peek();
	        	VarWithLineNo temp3 = currPlace.peek();
	        	
	        	String varType = n.leftType().toType().name().toString();
                if(!((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
                		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
                		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
                		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
                	if (n.right() instanceof Local_c) {
                		Local_c tempLocal = (Local_c)n.right();
                		HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
                		if (methodInfo != null) {
                			HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
                			if (lineInfo != null) {
    	        				if (lastGraphInfo.size() > 0) {
    	        					lineInfo.put(lineNo, lastGraphInfo.pop()); //check for whether back needs to take for second pass also
    	        					//update modifier boolean
    	        				} 
    	        				else {
    	        					lineInfo.put(lineNo, new HashMap<String, LinkedList<EdgeRep>>());
    		       					//update modifier boolean
    	        				}
                				
    	        				////////////for the closure//////////////////
    	        				String fldName = "";
                				String varName = "";
                				String lhsVar = "";
    	        				boolean path = true;
    	        				
    	        				if (temp2.name.equalsIgnoreCase("operator()")) {
    	        					if (n.target() instanceof Field_c) {
    	        						fldName = n.fieldInstance().name().toString();
    	        						Field_c tempField = (Field_c)n.target();
    	        						varName = tempField.fieldInstance().name().toString();
    	        					}
    	        					
    	        					//this path will never occur
    	        					if (n.target() instanceof Special_c) {
    	        						path = false;
    	        						lhsVar = n.fieldInstance().name().toString();
    	        					}
    	        				} 
    	        				else {
    	        					if (n.target() instanceof Special_c) {
    	        						varName = "this";
    	        					}
    	        					else {
    	        						varName = n.target().toString();
    	        					}
    	        					fldName = n.fieldInstance().name().toString();
    	        				}
    	        				
    	        				////////////for the closure//////////////////
    	        				
                				HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
                				if(varInfo != null && path) {
                					String rhsVar = tempLocal.name().toString();
                					
            	                	
            	                	LinkedList<EdgeRep> dest = varInfo.get(varName);
            	                	LinkedList<EdgeRep> src = varInfo.get(rhsVar);
            	                	if (dest != null && src != null) {
            	                		Iterator it = dest.iterator();
            	                		
            	                		while (it.hasNext()) {
            	                			
            	                			edgeNumber++;
            	                			String edgNameDestM2 = "";
            	                			
            	                			EdgeRep er = (EdgeRep)it.next();
            	                			
            	                			//the graph [doubt]
            	                			if(!er.desName.equalsIgnoreCase("Obj-null") && !(varInfo.containsKey(er.desName))) {
            		                    		varInfo.put(er.desName, new LinkedList<EdgeRep>());
            		                    		//update modifier boolean
            		                    	}
            	                			
            	                			LinkedList<EdgeRep> dest1 = varInfo.get(er.desName);
            	                			if (dest1 != null) {
            	                				Iterator it1 = dest1.iterator();
            	                				//replacing list
            	    	                		LinkedList<EdgeRep> tempDest = new LinkedList<EdgeRep>();
            	    	                		
            	    	                		while(it1.hasNext()) {
            	    	                			EdgeRep er1 = (EdgeRep)it1.next();
            	    	                			
            	    	                			if (!(er1.edgeType.equalsIgnoreCase("F") && (er1.fieldName.equalsIgnoreCase(fldName)))) {
            	    	                				EdgeRep er2 = new EdgeRep(er1.edgeType, er1.desName, er1.fieldName, er1.copyFlag);
            	    	                				er2.edgeName = er1.edgeName;
            	    	                				tempDest.addLast(er2);
            	    	                			}
            	    	                			else {
            	    	                				
            	    	                				if (dest.size() == 1) {
	            	    	                				Iterator it2 = src.iterator();
	            	    	                				while(it2.hasNext()) {
	                    	    	                			EdgeRep er3 = (EdgeRep)it2.next();
	                    	    	                			if (er1.edgeType.equalsIgnoreCase("F") && er1.desName.equalsIgnoreCase(er3.desName)) {
	                    	    	                				EdgeRep er2 = new EdgeRep(er1.edgeType, er1.desName, er1.fieldName, er1.copyFlag);
	                    	    	                				er1.edgeName = "";
	                    	    	                				//a check whether src or dest size should be greater than 1
	                    	    	                				//System.out.println("The size of src and dest: " + src.size() + ":" + dest.size());
	                    	    	                				if (dest.size() > 1) {
		            	               									er2.edgeName = ("zx"+edgeNumber);	
		            	               								}
	                    	    	                				if (src.size() > 1) {
		            	               									er2.edgeName = ("zx"+edgeNumber);	
		            	               								}
	                    	    	                				tempDest.addLast(er2);
	                    	    	                				break;
	                    	    	                			}
	                    	    	                		}
            	    	                				}	
            	    	                				
            	    	                				if (dest.size() > 1) {
            	    	                					EdgeRep er2 = new EdgeRep(er1.edgeType, er1.desName, er1.fieldName, er1.copyFlag);
                	    	                				er2.edgeName = er1.edgeName;
                	    	                				if (er1.edgeName.equalsIgnoreCase("")) {
                	    	                					edgNameDestM2 = ("zx"+edgeNumber);
                	    	                					er2.edgeName = ("zx"+edgeNumber);
                	    	                				}
                	    	                				else {
                	    	                					edgNameDestM2 = er1.edgeName;
                	    	                				}
                	    	                				tempDest.addLast(er2);
            	    	                				}
            	    	                				
            	    	                			}
            	    	                		}
            	    	                		
            	    	                		Iterator it2 = src.iterator();
            	    	                		while(it2.hasNext()) {
            	    	                			EdgeRep er1 = (EdgeRep)it2.next();
            	    	                			
            	    	                			Iterator it4 = tempDest.iterator();
            	    	                			boolean found = false;
            	    	                			while (it4.hasNext()) {
            	    	                				EdgeRep er3 = (EdgeRep)it4.next();
            	    	                				if (er3.edgeType.equalsIgnoreCase("F") && er1.desName.equalsIgnoreCase(er3.desName)) {
                	    	                				found = true;
                	    	                			}
            	    	                				if(found) {
            	    	                					break;
            	    	                				}
            	    	                			}
            	    	                			if(!found) {
            	    	                				EdgeRep er2 = new EdgeRep("F", er1.desName, fldName);
            	    	                				//a check whether src or dest size should be greater than 1 
            	    	                				//System.out.println("The size of src and dest: " + src.size() + ":" + dest.size());
            	    	                				if (dest.size() > 1) {
        	               									er2.edgeName = edgNameDestM2;	
        	               								}
            	    	                				else if (src.size() > 1) {
        	               									er2.edgeName = ("zx"+edgeNumber);	
        	               								}
                	    	                			tempDest.addLast(er2);
                	    	                			//update modifier boolean
            	    	                			}
            	    	                		}
            	    	                		varInfo.put(er.desName, tempDest);
            	                			}
            	                			
            	                			
            	                			//the sets
            								HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	                				if (setMethodInfo != null) {
        	                					HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        	                					if (placeInfo != null) {
        	                						HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
            	                					if (setDetails != null) {
            	                						HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
            	                						HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
            	                						if(!(er.desName.equalsIgnoreCase("Obj-null"))) {
            	                							
            	                							//for the gvs
	            	               							if (ovSet != null) {
	            	               								HashSet<String> ovs = ovSet.get("global-ovs");
	            	               								if (dest.size() > 1) {
	            	               									ovs.add(varName);
	            	               									ovs.add("zx"+edgeNumber);
	            	               								}
	            	               								if (src.size() > 1) {
	            	               									ovs.add(rhsVar);
	            	               								}
	            	               							}
            	                							
            	                							if (writeSet != null) {
            	                								HashSet<String> ws = writeSet.get(er.desName);
            	                								if (ws != null) {
            	                									//note sure whether contains works fine for HashSet
            	                									if (!ws.contains(fldName)) {
            	                									ws.add(fldName);
            	                									//update modifier boolean
            	                									}
            	                								}
            	                							}
            	                						}
            	                					}
        	                					}
        	                				}
            	                		}
            	                	}
                				}
                				
                				//this path will never occur
                				if(varInfo != null && !path) {

    	            				String rhsVar = tempLocal.name().toString();
    	            				
    	            				LinkedList<EdgeRep> src = varInfo.get(rhsVar);
    	                			LinkedList<EdgeRep> dest = varInfo.get(lhsVar);	
    	                			
    	                			if (src != null && dest != null) {
    	                				Iterator it = src.iterator();
    	                				
    	                				while (it.hasNext()) {
    	                					EdgeRep er = (EdgeRep)it.next();
    	                					
    	                					Iterator it1 = dest.iterator();
    	                					boolean found = false;
    	                					
    	                					while (it1.hasNext()) {
    	                						EdgeRep er1 = (EdgeRep)it1.next();
    	                						if((er.desName.equalsIgnoreCase(er1.desName))) {
    	    	                					found = true;
    	    	                				}
    	                						
    	                						if(found) {
    	    	                					break;
    	    	                				}
    	                					}
    	                					
    	                					if(!found) {
    	                						EdgeRep edgIncl = new EdgeRep("P",er.desName);
    	                            			dest.addLast(edgIncl);
    	                            			//update modifier boolean
    	    	                			}
    	                				}
    	                			}
                				}
                				
                				//the back up after the changes in points to graph. NOTE:##! check to stop whether it can be done once alone
    	        				varInfo = lineInfo.get(lineNo);
    	        				if (varInfo != null) {
    	        					lastGraphInfo.push(deepCopy(varInfo));
    	        				}
                			}
                		}
                    }
                	else if (n.right() instanceof NullLit_c) {
                		HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
                		if (methodInfo != null) {
                			HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
                			if (lineInfo != null) {
    	        				if (lastGraphInfo.size() > 0) {
    	        					lineInfo.put(lineNo, lastGraphInfo.pop()); //check for whether back needs to take for second pass also
    	        					//update modifier boolean
    	        				} 
    	        				else {
    	        					lineInfo.put(lineNo, new HashMap<String, LinkedList<EdgeRep>>());
    		       					//update modifier boolean
    	        				}
    	        				
    	        				
    	        				////////////for the closure//////////////////
    	        				String fldName = "";
                				String varName = "";
                				String lhsVar = "";
    	        				boolean path = true;
    	        				
    	        				if (temp2.name.equalsIgnoreCase("operator()")) {
    	        					if (n.target() instanceof Field_c) {
    	        						fldName = n.fieldInstance().name().toString();
    	        						Field_c tempField = (Field_c)n.target();
    	        						varName = tempField.fieldInstance().name().toString();
    	        					}
    	        					
    	        					//this case case never happens remove the code path = false below in future
    	        					if (n.target() instanceof Special_c) {
    	        						path = false;
    	        						lhsVar = n.fieldInstance().name().toString();
    	        					}
    	        				} 
    	        				else {
    	        					if (n.target() instanceof Special_c) {
    	        						varName = "this";
    	        					}
    	        					else {
    	        						varName = n.target().toString();
    	        					}
    	        					fldName = n.fieldInstance().name().toString();
    	        				}
    	        				
    	        				////////////for the closure//////////////////
                				
                				HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo); 
                				if(varInfo != null && path) {
            						LinkedList<EdgeRep> dest = varInfo.get(varName);
            						
            						if (dest != null) {
            							Iterator it = dest.iterator();
            							
            							while (it.hasNext()) {
            								EdgeRep er = (EdgeRep)it.next();
            								edgeNumber++;
            	                			String edgNameDestM2 = "";
            								
            	                			//the graph [doubt]
            	                			if(!(varInfo.containsKey(er.desName))) {
            		                    		varInfo.put(er.desName, new LinkedList<EdgeRep>());
            		                    		//update modifier boolean
            		                    	}
            	                			
            	                			LinkedList<EdgeRep> dest1 = varInfo.get(er.desName);
            	                			if (dest1 != null) {
            	                				Iterator it1 = dest1.iterator();
            	                				LinkedList<EdgeRep> tempDest = new LinkedList<EdgeRep>();
            	                				
            	                				while (it1.hasNext()) {
            	                					EdgeRep er1 = (EdgeRep)it1.next();
            	                					
            	                					if (!(er1.edgeType.equalsIgnoreCase("F") && (er1.fieldName.equalsIgnoreCase(fldName)))) {
            	    	                				EdgeRep er2 = new EdgeRep(er1.edgeType, er1.desName, er1.fieldName, er1.copyFlag);
            	    	                				er2.edgeName = er1.edgeName;
            	    	                				tempDest.addLast(er2);
            	    	                			}
            	                					else {
            	                						
            	                						if (dest.size() == 1) {
	            	                						if (er1.edgeType.equalsIgnoreCase("F") && er1.desName.equalsIgnoreCase("Obj-null")) {
	            	                							EdgeRep er2 = new EdgeRep(er1.edgeType, er1.desName, er1.fieldName, er1.copyFlag);
	            	                							er2.edgeName = er1.edgeName;
	                	    	                				tempDest.addLast(er2);
	                	    	                			}
	            	                						else {
	            	                							//update modifier boolean
	            	                						}
            	                						}
            	                						
            	                						if (dest.size() > 1) {
            	    	                					EdgeRep er2 = new EdgeRep(er1.edgeType, er1.desName, er1.fieldName, er1.copyFlag);
                	    	                				er2.edgeName = er1.edgeName;
                	    	                				if (er1.edgeName.equalsIgnoreCase("")) {
                	    	                					edgNameDestM2 = ("zx"+edgeNumber);
                	    	                					er2.edgeName = ("zx"+edgeNumber);
                	    	                				}
                	    	                				else {
                	    	                					edgNameDestM2 = er1.edgeName;
                	    	                				}
                	    	                				tempDest.addLast(er2);
            	    	                				}
            	                						
            	                					}
            	                				}
            	                				
            	                				Iterator it4 = tempDest.iterator();
            	                				boolean found = false;
            	                				while (it4.hasNext()) {
            	                					EdgeRep er3 = (EdgeRep)it4.next();
            	                					if (er3.edgeType.equalsIgnoreCase("F") && er3.desName.equalsIgnoreCase("Obj-null")) {
            	                						found = true;
            	                					}
            	                					if (found) {
            	                						break;
            	                					}
            	                				}
            	                				
            	                				if(!found) {
            	                					EdgeRep er2 = new EdgeRep("F", "Obj-null", fldName);
            	                					
            	                					if (dest.size() > 1) {
    	               									er2.edgeName = edgNameDestM2;	
    	               								}
            	                					
            	    	                			tempDest.addLast(er2);
            	    	                			//update modifier boolean
            	                				}
            	                				
            	                				varInfo.put(er.desName, tempDest);
            	                			}
            	                			
            								//the sets
            								HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	                				if (setMethodInfo != null) {
        	                					HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        	                					if (placeInfo != null) {
        	                						HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
            	                					if (setDetails != null) {
            	                						HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
            	                						HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
            	                						if(!(er.desName.equalsIgnoreCase("Obj-null"))) {
            	                							
            	                							//for the gvs
	            	               							if (ovSet != null) {
	            	               								HashSet<String> ovs = ovSet.get("global-ovs");
	            	               								if (dest.size() > 1) {
	            	               									ovs.add(varName);	
	            	               									ovs.add("zx"+edgeNumber);
	            	               								}
	            	               							}
            	                							
            	                							if (writeSet != null) {
            	                								HashSet<String> ws = writeSet.get(er.desName);
            	                								if (ws != null) {
            	                									//note sure whether contains works fine for HashSet
            	                									if (!ws.contains(fldName)) {
            	                									ws.add(fldName);
            	                									//update modifier boolean
            	                									}
            	                								}
            	                							}
            	                						}
            	                					}
        	                					}
        	                				}
        	                				
            							}
            						}
                				}
                				
                				//this case will never hapen
                				if(varInfo != null && !path) {
                					varInfo.put(lhsVar, new LinkedList<EdgeRep>());
                					LinkedList<EdgeRep> edgeIncl = varInfo.get(lhsVar);
                                	
                                	if (edgeIncl != null) {
                                		Iterator it = edgeIncl.iterator();
                                		boolean found = false;
                                		while (it.hasNext()) {
                                			EdgeRep er = (EdgeRep)it.next();
                                			
                                			if (er.desName.equalsIgnoreCase("Obj-null")) {
                                				found = true;
                                				break;
                                			}
                                		}
                                		if (!found) {
                                			EdgeRep edgeInfo = new EdgeRep("P","Obj-null");
                                			edgeIncl.addLast(edgeInfo);
                                			//update modifier boolean
                                		}
                                	}
                				}
                				//the back up after the changes in points to graph. NOTE:##! check to stop whether it can be done once alone
    	        				varInfo = lineInfo.get(lineNo);
    	        				if (varInfo != null) {
    	        					lastGraphInfo.push(deepCopy(varInfo));
    	        				}
                			}
                		}
                	}
                	else if (n.right() instanceof Field_c) {

                		Field_c tempField1 = (Field_c)n.right();
                		HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
                		if (methodInfo != null) {
                			HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
                			if (lineInfo != null) {
    	        				if (lastGraphInfo.size() > 0) {
    	        					lineInfo.put(lineNo, lastGraphInfo.pop()); //check for whether back needs to take for second pass also
    	        					//update modifier boolean
    	        				} 
    	        				else {
    	        					lineInfo.put(lineNo, new HashMap<String, LinkedList<EdgeRep>>());
    		       					//update modifier boolean
    	        				}
                				
    	        				////////////for the closure//////////////////
    	        				String fldName = "";
                				String varName = "";
                				String lhsVar = "";
    	        				boolean path = true;
    	        				
    	        				if (temp2.name.equalsIgnoreCase("operator()")) {
    	        					if (n.target() instanceof Field_c) {
    	        						fldName = n.fieldInstance().name().toString();
    	        						Field_c tempField = (Field_c)n.target();
    	        						varName = tempField.fieldInstance().name().toString();
    	        					}
    	        					
    	        					//this path will never occur
    	        					if (n.target() instanceof Special_c) {
    	        						path = false;
    	        						lhsVar = n.fieldInstance().name().toString();
    	        					}
    	        				} 
    	        				else {
    	        					if (n.target() instanceof Special_c) {
    	        						varName = "this";
    	        					}
    	        					else {
    	        						varName = n.target().toString();
    	        					}
    	        					fldName = n.fieldInstance().name().toString();
    	        				}
    	        				
    	        				////////////for the closure//////////////////
    	        				
                				HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
                				if(varInfo != null && path) {
                					String rhsVar = tempField1.fieldInstance().name().toString();
                					
            	                	
            	                	LinkedList<EdgeRep> dest = varInfo.get(varName);
            	                	LinkedList<EdgeRep> src = varInfo.get(rhsVar);
            	                	if (dest != null && src != null) {
            	                		Iterator it = dest.iterator();
            	                		
            	                		while (it.hasNext()) {
            	                			
            	                			edgeNumber++;
            	                			String edgNameDestM2 = "";
            	                			
            	                			EdgeRep er = (EdgeRep)it.next();
            	                			
            	                			//the graph [doubt]
            	                			if(!(varInfo.containsKey(er.desName))) {
            		                    		varInfo.put(er.desName, new LinkedList<EdgeRep>());
            		                    		//update modifier boolean
            		                    	}
            	                			
            	                			LinkedList<EdgeRep> dest1 = varInfo.get(er.desName);
            	                			if (dest1 != null) {
            	                				Iterator it1 = dest1.iterator();
            	                				//replacing list
            	    	                		LinkedList<EdgeRep> tempDest = new LinkedList<EdgeRep>();
            	    	                		
            	    	                		while(it1.hasNext()) {
            	    	                			EdgeRep er1 = (EdgeRep)it1.next();
            	    	                			
            	    	                			if (!(er1.edgeType.equalsIgnoreCase("F") && (er1.fieldName.equalsIgnoreCase(fldName)))) {
            	    	                				EdgeRep er2 = new EdgeRep(er1.edgeType, er1.desName, er1.fieldName, er1.copyFlag);
            	    	                				er2.edgeName = er1.edgeName;
            	    	                				tempDest.addLast(er2);
            	    	                			}
            	    	                			else {
            	    	                				if (dest.size() == 1) {
	            	    	                				Iterator it2 = src.iterator();
	            	    	                				while(it2.hasNext()) {
	                    	    	                			EdgeRep er3 = (EdgeRep)it2.next();
	                    	    	                			if (er1.edgeType.equalsIgnoreCase("F") && er1.desName.equalsIgnoreCase(er3.desName)) {
	                    	    	                				EdgeRep er2 = new EdgeRep(er1.edgeType, er1.desName, er1.fieldName, er1.copyFlag);
	                    	    	                				er1.edgeName = "";
	                    	    	                				//a check whether src or dest size should be greater than 1
	                    	    	                				//System.out.println("The size of src and dest: " + src.size() + ":" + dest.size());
	                    	    	                				if (dest.size() > 1) {
		            	               									er2.edgeName = ("zx"+edgeNumber);	
		            	               								}
	                    	    	                				if (src.size() > 1) {
		            	               									er2.edgeName = ("zx"+edgeNumber);	
		            	               								}
	                    	    	                				tempDest.addLast(er2);
	                    	    	                				break;
	                    	    	                			}
	                    	    	                		}
            	    	                				}	
            	    	                				
            	    	                				if (dest.size() > 1) {
            	    	                					EdgeRep er2 = new EdgeRep(er1.edgeType, er1.desName, er1.fieldName, er1.copyFlag);
                	    	                				er2.edgeName = er1.edgeName;
                	    	                				if (er1.edgeName.equalsIgnoreCase("")) {
                	    	                					edgNameDestM2 = ("zx"+edgeNumber);
                	    	                					er2.edgeName = ("zx"+edgeNumber);
                	    	                				}
                	    	                				else {
                	    	                					edgNameDestM2 = er1.edgeName;
                	    	                				}
                	    	                				tempDest.addLast(er2);
            	    	                				}
            	    	                			}
            	    	                		}
            	    	                		
            	    	                		Iterator it2 = src.iterator();
            	    	                		while(it2.hasNext()) {
            	    	                			EdgeRep er1 = (EdgeRep)it2.next();
            	    	                			
            	    	                			Iterator it4 = tempDest.iterator();
            	    	                			boolean found = false;
            	    	                			while (it4.hasNext()) {
            	    	                				EdgeRep er3 = (EdgeRep)it4.next();
            	    	                				if (er3.edgeType.equalsIgnoreCase("F") && er1.desName.equalsIgnoreCase(er3.desName)) {
                	    	                				found = true;
                	    	                			}
            	    	                				if(found) {
            	    	                					break;
            	    	                				}
            	    	                			}
            	    	                			if(!found) {
            	    	                				EdgeRep er2 = new EdgeRep("F", er1.desName, fldName);
            	    	                				//a check whether src or dest size should be greater than 1 
            	    	                				//System.out.println("The size of src and dest: " + src.size() + ":" + dest.size());
            	    	                				if (dest.size() > 1) {
        	               									er2.edgeName = edgNameDestM2;	
        	               								}
            	    	                				else if (src.size() > 1) {
        	               									er2.edgeName = ("zx"+edgeNumber);	
        	               								}
                	    	                			tempDest.addLast(er2);
                	    	                			//update modifier boolean
            	    	                			}
            	    	                		}
            	    	                		varInfo.put(er.desName, tempDest);
            	                			}
            	                			
            	                			
            	                			//the sets
            								HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	                				if (setMethodInfo != null) {
        	                					HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        	                					if (placeInfo != null) {
        	                						HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
            	                					if (setDetails != null) {
            	                						HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
            	                						HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
            	                						if(!(er.desName.equalsIgnoreCase("Obj-null"))) {
            	                							
            	                							//for the gvs
	            	               							if (ovSet != null) {
	            	               								HashSet<String> ovs = ovSet.get("global-ovs");
	            	               								if (dest.size() > 1) {
	            	               									ovs.add(varName);
	            	               									ovs.add("zx"+edgeNumber);
	            	               								}
	            	               								if (src.size() > 1) {
	            	               									ovs.add(rhsVar);
	            	               								}
	            	               							}
            	                							
            	                							if (writeSet != null) {
            	                								HashSet<String> ws = writeSet.get(er.desName);
            	                								if (ws != null) {
            	                									//note sure whether contains works fine for HashSet
            	                									if (!ws.contains(fldName)) {
            	                									ws.add(fldName);
            	                									//update modifier boolean
            	                									}
            	                								}
            	                							}
            	                						}
            	                					}
        	                					}
        	                				}
            	                		}
            	                	}
                				}
                				
                				
                				//the back up after the changes in points to graph. NOTE:##! check to stop whether it can be done once alone
    	        				varInfo = lineInfo.get(lineNo);
    	        				if (varInfo != null) {
    	        					lastGraphInfo.push(deepCopy(varInfo));
    	        				}
                			}
                		}
                	}
                }
                else {
                	
                	////////////for the closure//////////////////
    				String fldName = "";
    				String varName = "";
    				String sprField = "";
    				boolean path = true;
    				
    				if (temp2.name.equalsIgnoreCase("operator()")) {
    					if (n.target() instanceof Field_c) {
    						fldName = n.fieldInstance().name().toString();
    						Field_c tempField = (Field_c)n.target();
    						varName = tempField.fieldInstance().name().toString();
    					}
    					
    					if (n.target() instanceof Special_c) {
    						path = false;
    					}
    				} 
    				else {
    					if (n.target() instanceof Special_c) {
    						varName = "this";
    						
    						Special_c specialFld = (Special_c)n.target();
    						if (specialFld.kind().toString().equalsIgnoreCase("super")) {
    							sprField = "spr.";
    						}
    					}
    					else {
    						varName = n.target().toString();
    					}
    					fldName = sprField+n.fieldInstance().name().toString();
    				}
    				
    				////////////for the closure//////////////////
                	
                	HashMap<String, LinkedList<EdgeRep>> varInfo = lastGraphInfo.peek();
                	if (varInfo != null && path) {
                		LinkedList<EdgeRep> var = varInfo.get(varName);
                		if (var != null) {
                			if (var.size() > 1) {
                				Iterator it = var.iterator();
                				while (it.hasNext()) {
                					EdgeRep er = (EdgeRep)it.next();
                					//the sets 
                					HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	                    		if (setMethodInfo != null) {
        	                    			HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        	                    			if (placeInfo != null) {
        	                    				HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
	        		                    		if (setDetails != null) {
	        		                    			HashMap<String, HashSet<String>> writeSet = setDetails.get("MWS");
	        		                    			HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
	        		                    			HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
	        		                    			if(!(er.desName.equalsIgnoreCase("Obj-null"))) {
	        		                    				
	        		                    				if (ovSet != null) {
        	            									HashSet<String> set1 = ovSet.get("global-ovs");
        	            									if (set1 != null) {
        	            										set1.add(varName);
        	            									}
        	            								}
	        		                    				
	        		                    				if (writeSet != null && readSet != null) {
	        		                    					if ((varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) ||
	        		                    							(varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("Rail"))) {
	        		                    						HashSet<String> rs = readSet.get(er.desName);
	        		                    						if (rs != null) {
	        		                    							//note sure whether contains works fine for HashSet
		        		                    						if (!rs.contains(fldName)) {
		        		                    							rs.add(fldName);
		        		                    							//update modifier boolean
		        		                    						}
	        		                    						}
	        		                    					}
	        		                    					else {
	        		                    						HashSet<String> ws = writeSet.get(er.desName);
		        		                    					if (ws != null) {
		        		                    						//note sure whether contains works fine for HashSet
		        		                    						if (!ws.contains(fldName)) {
		        		                    							ws.add(fldName);
		        		                    							//update modifier boolean
		        		                    						}
		        		                    					}
	        		                    					}
	        		                    				}
	        		                    			}
	        		                    		}
        	                    			}
        		                    	}
                				}
                			}
                			else {
                				Iterator it = var.iterator();
                				while (it.hasNext()) {
                					EdgeRep er = (EdgeRep)it.next();
                					//the sets 
                					HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	                    		if (setMethodInfo != null) {
        	                    			HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        	                    			if (placeInfo != null) {
        	                    				HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
	        		                    		if (setDetails != null) {
	        		                    			HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
	        		                    			HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
	        		                    			if(!(er.desName.equalsIgnoreCase("Obj-null"))) {
	        		                    				
	        		                    				
	        		                    				if (writeSet != null && readSet != null) {
	        		                    					if ((varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) ||
	        		                    							(varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("Rail"))) {
	        		                    						HashSet<String> rs = readSet.get(er.desName);
	        		                    						if (rs != null) {
	        		                    							//note sure whether contains works fine for HashSet
		        		                    						if (!rs.contains(fldName)) {
		        		                    							rs.add(fldName);
		        		                    							//update modifier boolean
		        		                    						}
	        		                    						}
	        		                    					}
	        		                    					else {
	        		                    						HashSet<String> ws = writeSet.get(er.desName);
		        		                    					if (ws != null) {
		        		                    						//note sure whether contains works fine for HashSet
		        		                    						if (!ws.contains(fldName)) {
		        		                    							ws.add(fldName);
		        		                    							//update modifier boolean
		        		                    						}
		        		                    					}
	        		                    					}
	        		                    				}
	        		                    				
	        		                    				
	        		                    			}
	        		                    		}
        	                    			}
        		                    	}
                				}
                			}
                		}
                	}
                }
            }
            
            /* the printer code */
            /*
        	VarWithLineNo temp1 = currClass.peek();
        	VarWithLineNo temp2 = null;
        	VarWithLineNo temp3 = null;
        	boolean goThrough = true;
        	//if ((temp1.name.equalsIgnoreCase("Histogram") || temp1.name.equalsIgnoreCase("test") || temp1.name.equalsIgnoreCase("$Closure$0"))) {
        		if (currMethod.size() > 0) {
        			temp2 = currMethod.peek();
        		} 
        		else {
        			goThrough = false;
        		}
        		if (currPlace.size() > 0) {
        		 temp3 = currPlace.peek();
        		}
        		else {
        			goThrough = false;
        		}
        	//}
        	//System.out.println("I AM PRINTING: " + temp1.lineNo +":"+ temp2.lineNo +":"+ temp3.lineNo +":" + lineNo);
        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
        	if (methodInfo != null && goThrough) {
        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
        		if (lineInfo != null) {
        			HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
        			if (varInfo != null) {
        				System.out.println("GRAPH AFTER FIELD ASSIGN: " + lineNo);
        				Iterator it = varInfo.entrySet().iterator();
        	        	while (it.hasNext()) {
        	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
        	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
        	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
        	        		Iterator it1 = edgeIncl.iterator();
        	        		while (it1.hasNext())
        	        		{
        	        			EdgeRep er = (EdgeRep)it1.next();
        	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
        	        					+ er.fieldName + " EDGE NAME:" + er.edgeName);
        	        		}
        	        	}
        			}
        		}
        		System.out.println("--------------------------------------------------end of field assign--------------------------------------");
        	}
        	*/
        	/* the printer code */
            /* Nobita code */
            
            
            if (isMutableStruct(n.fieldInstance().type())) {
                w.write(".clone()");
            }
        } else if (n.target() instanceof TypeNode || n.target() instanceof Local || n.target() instanceof Lit) {
            // target has no side effects--evaluate it more than once
            Binary.Operator op = n.operator().binaryOperator();
            Name methodName = X10Binary_c.binaryMethodName(op);
            if (n.target() instanceof TypeNode)
                er.printType(n.target().type(), 0);
            else
                tr.print(n, n.target(), w);
            w.write(".");
            w.write(Emitter.mangleToJava(n.name().id()));
            w.write(" ");
            w.write(" = ");
            tr.print(n, n.target(), w);
            w.write(".");
            w.write(Emitter.mangleToJava(n.name().id()));
            w.write(".");
            w.write(Emitter.mangleToJava(methodName));
            w.write("(");
            tr.print(n, n.right(), w);
            w.write(")");
        } else {
            // x.f += e
            // -->
            // new Object() { T eval(R target, T right) { return (target.f =
            // target.f.add(right)); } }.eval(x, e)
            Binary.Operator op = n.operator().binaryOperator();
            Name methodName = X10Binary_c.binaryMethodName(op);
            // TODO pretty print
            w.write("new " + JAVA_IO_SERIALIZABLE + "() {");
            w.allowBreak(0);
            w.write("final ");
            er.printType(n.type(), PRINT_TYPE_PARAMS);
            w.write(" eval(");
            er.printType(n.target().type(), PRINT_TYPE_PARAMS);
            w.write(" target, ");
            er.printType(n.right().type(), PRINT_TYPE_PARAMS);
            w.write(" right) {");
            w.allowBreak(0);
            w.write("return (target.");
            w.write(Emitter.mangleToJava(n.name().id()));
            w.write(" = ");
            w.write("target.");
            w.write(Emitter.mangleToJava(n.name().id()));
            w.write(".");
            w.write(Emitter.mangleToJava(methodName));
            w.write("(right));");
            w.allowBreak(0);
            w.write("} }.eval(");
            tr.print(n, n.target(), w);
            w.write(", ");
            tr.print(n, n.right(), w);
            w.write(")");
        }
    }

    @Override
    public void visit(SettableAssign_c n) {
        SettableAssign_c a = n;
        Expr array = a.array();
        List<Expr> index = a.index();

        boolean effects = er.hasEffects(array);
        for (Expr e : index) {
            if (effects) break;
            if (er.hasEffects(e)) effects = true;
        }

        TypeSystem ts = tr.typeSystem();
        Context context = tr.context();
        Type t = n.leftType();

        boolean nativeop = false;
        if (isPrimitive(t) || t.isString()) {
            nativeop = true;
        }

        MethodInstance mi = n.methodInstance();
        boolean superUsesClassParameter = !mi.flags().isStatic(); // &&
                                                                  // overridesMethodThatUsesClassParameter(mi);

        if (n.operator() == Assign.ASSIGN) {
            // Look for the appropriate set method on the array and emit native
            // code if there is an @Native annotation on it.
            String pat = Emitter.getJavaImplForDef(mi.x10Def());
            if (pat != null) {
        		List<String> params = new ArrayList<String>(index.size());
                List<Expr> args = new ArrayList<Expr>(index.size() + 1);
                // args.add(array);
                args.add(n.right());
                for (int i = 0; i < index.size(); ++i) {
        		    params.add(mi.def().formalNames().get(i).name().toString());
                	args.add(index.get(i));
                }
            	
                er.emitNativeAnnotation(pat, array, mi.x10Def().typeParameters(), mi.typeParameters(), params, args, Collections.<ParameterType>emptyList(), Collections.<Type> emptyList());
                return;
            } else {
                // otherwise emit the hardwired code.
                tr.print(n, array, w);
                w.write(".set");
                w.write("(");
                tr.print(n, n.right(), w);
                if (index.size() > 0) w.write(", ");
                new Join(er, ", ", index).expand(tr);
                w.write(")");
            }
        } else if (!effects) {
            Binary.Operator op = n.operator().binaryOperator();
            Name methodName = X10Binary_c.binaryMethodName(op);
            TypeSystem xts = ts;
            if (isPrimitive(t) && isRail(array.type())) {
                w.write("(");
                w.write("(");
                er.printType(t, 0);
                w.write("[])");
                tr.print(n, array, w);
                w.write(".value");
                w.write(")");
                // LONG_RAIL: unsafe int cast
                w.write("[(int)");
                new Join(er, ", ", index).expand(tr);
                w.write("]");
                w.write(" ");
                w.write(op.toString());
                w.write("=");
                w.write(" ");
                tr.print(n, n.right(), w);
                return;
            }

            tr.print(n, array, w);
            w.write(".set");
            w.write("((");
            tr.print(n, array, w);
            w.write(").$apply(");
            new Join(er, ", ", index).expand(tr);
            w.write(")");
            if (nativeop) {
                w.write(" ");
                w.write(op.toString());
                tr.print(n, n.right(), w);
            } else {
                w.write(".");
                w.write(Emitter.mangleToJava(methodName));
                w.write("(");
                tr.print(n, n.right(), w);
                w.write(")");
            }
            if (index.size() > 0) w.write(", ");
            new Join(er, ", ", index).expand(tr);
            w.write(")");
        } else {
            // new Object() { T eval(R target, T right) { return (target.f =
            // target.f.add(right)); } }.eval(x, e)
            Binary.Operator op = n.operator().binaryOperator();
            Name methodName = X10Binary_c.binaryMethodName(op);
            TypeSystem xts = ts;
            if (isPrimitive(t) && isRail(array.type())) {
                w.write("(");
                w.write("(");
                er.printType(t, 0);
                w.write("[])");
                tr.print(n, array, w);
                w.write(".value");
                w.write(")");
                // LONG_RAIL: unsafe int cast
                w.write("[(int)");
                new Join(er, ", ", index).expand(tr);
                w.write("]");
                w.write(" ");
                w.write(op.toString());
                w.write("=");
                w.write(" ");
                tr.print(n, n.right(), w);
                return;
            }

            // TODO pretty print
            w.write("new " + JAVA_IO_SERIALIZABLE + "() {");
            w.allowBreak(0);
            w.write("final ");
            er.printType(n.type(), PRINT_TYPE_PARAMS);
            w.write(" eval(");
            er.printType(array.type(), PRINT_TYPE_PARAMS);
            w.write(" array");
            {
                int i = 0;
                for (Expr e : index) {
                    w.write(", ");
                    er.printType(e.type(), PRINT_TYPE_PARAMS);
                    w.write(" ");
                    w.write("i" + i);
                    i++;
                }
            }
            w.write(", ");
            er.printType(n.right().type(), PRINT_TYPE_PARAMS);
            w.write(" right) {");
            w.allowBreak(0);
            if (!n.type().isVoid()) {
                w.write("return ");
            }
            w.write("array.set");
            w.write("(");

            w.write(" array.$apply(");
            {
                int i = 0;
                for (Expr e : index) {
                    if (i != 0) w.write(", ");
                    w.write("i" + i);
                    i++;
                }
            }
            w.write(")");
            if (nativeop) {
                w.write(" ");
                w.write(op.toString());
                w.write(" right");
            } else {
                w.write(".");
                w.write(Emitter.mangleToJava(methodName));
                w.write("(right)");
            }
            if (index.size() > 0) w.write(", ");
            {
                int i = 0;
                for (Expr e : index) {
                    if (i != 0) w.write(", ");
                    w.write("i" + i);
                    i++;
                }
            }
            w.write(");");
            w.allowBreak(0);
            w.write("} }.eval(");
            tr.print(n, array, w);
            if (index.size() > 0) w.write(", ");
            new Join(er, ", ", index).expand();
            w.write(", ");
            tr.print(n, n.right(), w);
            w.write(")");
        }
    }
    
    @Override
    public void visit(X10Binary_c n) {
        Expr left = n.left();
        Type l = left.type();
        Expr right = n.right();
        Type r = right.type();
        TypeSystem xts = tr.typeSystem();
        Binary.Operator op = n.operator();

        if (l.isNumeric() && r.isNumeric() || l.isBoolean() && r.isBoolean() || l.isChar() && r.isChar()) {
            prettyPrint(n);
            return;
        }

        if (op == Binary.EQ) {
            // SYNOPSIS: #0 == #1
            // TODO generalize for reference type
            if (l.isNull() || r.isNull()) {
            	// ((#0) == (#1))
                w.write("((");
                er.prettyPrint(left, tr);
                w.write(") == (");
                er.prettyPrint(right, tr);
                w.write("))");
            } else {
                // x10.rtt.Equality.equalsequals(#0,#1)
                w.write("x10.rtt.Equality.equalsequals(");
                if (needExplicitBoxing(l)) {
                    er.printBoxConversion(l);
                }
                w.write("("); // required for printBoxConversion
                er.prettyPrint(left, tr);
                w.write(")");
                w.write(",");
                if (needExplicitBoxing(r)) {
                    er.printBoxConversion(r);
                }
                w.write("("); // required for printBoxConversion
                er.prettyPrint(right, tr);
            	w.write("))");
            }
            return;
        }

        if (op == Binary.NE) {
            // SYNOPSIS: #0 != #1
            // TODO generalize for reference type
            if (l.isNull() || r.isNull()) {
            	// ((#0) != (#1))
                w.write("((");
                er.prettyPrint(left, tr);
                w.write(") != (");
                er.prettyPrint(right, tr);
                w.write("))");
            } else {
            	// (!x10.rtt.Equality.equalsequals(#0,#1))
            	w.write("(!x10.rtt.Equality.equalsequals(");
                if (needExplicitBoxing(l)) {
                    er.printBoxConversion(l);
                }
                w.write("(");
                er.prettyPrint(left, tr);
                w.write(")");
            	w.write(",");
                if (needExplicitBoxing(r)) {
                    er.printBoxConversion(r);
                }
                w.write("(");
                er.prettyPrint(right, tr);
            	w.write(")))");
            }
            return;
        }

        if (op == Binary.ADD && (l.isString() || r.isString())) {
            prettyPrint(n);
            return;
        }
        if (n.invert()) {
            Name methodName = X10Binary_c.invBinaryMethodName(op);
            if (methodName != null) {
                er.generateStaticOrInstanceCall(n.position(), right, methodName, left);
                return;
            }
        } else {
            Name methodName = X10Binary_c.binaryMethodName(op);
            if (methodName != null) {
                er.generateStaticOrInstanceCall(n.position(), left, methodName, right);
                return;
            }
        }
        throw new InternalCompilerError("No method to implement " + n, n.position());
    }

    // This is an enhanced version of Binary_c#prettyPrint(CodeWriter,
    // PrettyPrinter)
    private void prettyPrint(X10Binary_c n) {
        Expr left = n.left();
        Type l = left.type();
        Expr right = n.right();
        Type r = right.type();
        Binary.Operator op = n.operator();

        boolean asPrimitive = false;
        if (op == Binary.EQ || op == Binary.NE) {
            if (l.isNumeric() && r.isNumeric() || l.isBoolean() && r.isBoolean() || l.isChar() && r.isChar()) {
                asPrimitive = true;
            }
        }

        boolean needParenl = false;
        if (asPrimitive) {
            // TODO:CAST
            w.write("(");
        	w.write("(");
        	er.printType(l, 0);
        	w.write(") ");
        }
        n.printSubExpr(left, true, w, tr);
        if (needParenl) w.write(")");
        if (asPrimitive) w.write(")");
        w.write(" ");
        w.write(op.toString());
        w.write(" ");
        if (asPrimitive) {
            // TODO:CAST
            w.write("(");
        	w.write("(");
        	er.printType(r, 0);
        	w.write(") ");
        }
        n.printSubExpr(right, false, w, tr);
        if (asPrimitive) w.write(")");
    }

    private static boolean allMethodsFinal(X10ClassDef def) {
    	return def.flags().isFinal() || def.isStruct();
    }
    private static boolean doesNotHaveMethodBody(X10ClassDef def) {
    	// for Comparable[T].compareTo(T)
    	// TODO expand @Native annotation of interface method to the types that implement the interface and don't have its implementation.
    	return def.flags().isInterface();
//    	return false;
    }
    private static boolean canBeNonVirtual(X10ClassDef def) {
    	return allMethodsFinal(def) || doesNotHaveMethodBody(def);
    }
    
    // TODO consolidate isPrimitive(Type) and needExplicitBoxing(Type).
    // return all X10 types that are mapped to Java primitives and require explicit boxing
    public static boolean needExplicitBoxing(Type t) {
        return isPrimitive(t);
    }
    public static boolean isBoxedType(Type t) {
        // void is included here, because synthetic methods have no definition and are reported as having type (void)
        return !(isPrimitive(t) || t.isVoid());
    }

    @Override
    public void visit(X10Call_c c) {
    	
    	/* Nobita code */
		String funName = c.name().toString();
		
		//for the inter-procedural analysis
		//System.out.println("The call name is: " + funName);
		boolean objParaPresent = false;
		
		//for the async closure
		if (funName.equalsIgnoreCase("runAsync")) {
			atCall = true;
		}
		
		//for the second pass
		//System.out.println("the line number at call[CALL]: " + lineNo);
		if (iterationCount > iterationCountSupp)  {
			if (funName.equalsIgnoreCase("runAsync") || funName.equalsIgnoreCase("runAt")) {
				savedObj = new Stack<String>();
				int closureLineNo = lineNo + 1;
				String closureName = lineClosure.get(closureLineNo);
				
				HashMap <String, String> varClosure = closureVar.get(closureName);
				HashMap<String, ForClosureObject> objClosure = closureObj.get(closureName);
				
				if (varClosure != null) {
					
					Iterator it = varClosure.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<String, String> phase3 = (Map.Entry<String, String>)it.next();
						
						if (!(phase3.getValue().equalsIgnoreCase("multiple"))) {
							
							String objName = phase3.getValue();
							
							if (!(savedObj.contains(objName))) {
								
								savedObj.push(objName);
								ForClosureObject fco = objClosure.get(objName);
								
								if (fco != null) {
									LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
									
									Iterator it1 = llFCOF.iterator();
									while (it1.hasNext()) {
										ForClosureObjectField fcof = (ForClosureObjectField)it1.next();
										
										if (fcof.tempStoredName.equalsIgnoreCase("NR")) {
											PrettyPrinterTemporary(fcof.fieldObjName, objClosure);
										}
										else {
											w.newline();
											String writerType = fcof.fieldType;
											if (fcof.fieldType.equalsIgnoreCase("x10.lang.Long")) {
												writerType = "long";
											}
											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Float")) {
												writerType = "float";
											}
											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Double")) {
												writerType = "double";
											}
											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Char")) {
												writerType = "char";
											}
											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.String")) {
												writerType = "java.lang.String";
											}
											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Boolean")) {
												writerType = "boolean";
											}
											else {
												String tempTyCh = fcof.fieldType;
												String resultStr = " ";
												int j = tempTyCh.length()-1;
												for(int i = 0; i<=j ; i++) {
													if (i <= j-3) {
														int k = i;
														if (tempTyCh.charAt(i) == 'l' && tempTyCh.charAt(++k) == 'a' && 
																tempTyCh.charAt(++k) == 'n' && tempTyCh.charAt(++k) == 'g') {
															resultStr = resultStr + "core";
															i = i +3;
														}
														else {
															
															if (tempTyCh.charAt(i) == '[') {
																resultStr = resultStr + "<";
															}
															else if(tempTyCh.charAt(i) == ']') {
																resultStr = resultStr + ">";
															}
															else {
																resultStr = resultStr + tempTyCh.charAt(i);
															}
														}
													}
													else {
														if (tempTyCh.charAt(i) == '[') {
															resultStr = resultStr + "<";
														}
														else if(tempTyCh.charAt(i) == ']') {
															resultStr = resultStr + ">";
														}
														else {
															resultStr = resultStr + tempTyCh.charAt(i);
														}
													}
												}
												
												writerType = resultStr;
											}
											
											
											w.write("final ");
											w.write(writerType);
											w.write(" ");
											w.write(fcof.tempStoredName);
											w.write(" = ");
											w.write(fco.varName);
											w.write(".");
											w.write(fcof.fieldName);
											w.write(";");
											w.newline();
											
										}
									}
									
								}
							}	
						}
					}
				}
				
				savedObj = new Stack<String>();
			}
		}
		
		//for the second pass changing the sending constructor
		if (funName.equalsIgnoreCase("runAsync") || funName.equalsIgnoreCase("runAt") || funName.equalsIgnoreCase("evalAt")) {
			changeConstruct = true;
		}
		
		String className = "";
		String desObj = "";
		String desVar = "";
		boolean path = false;
		VarWithLineNo temp1 = currClass.peek();
		VarWithLineNo temp2 = null;
		VarWithLineNo temp3 = null;
		if (currMethod.size() > 0) {
			temp2 = currMethod.peek();
			temp3 = currPlace.peek();
			path = true;
		}
    	if (!(c.name().toString().equalsIgnoreCase("Places")) && !(c.name().toString().equalsIgnoreCase("runAt")) && !(c.name().toString().equalsIgnoreCase("runAsync"))
    			&& !(c.name().toString().equalsIgnoreCase("wrapAtChecked")) && !(c.name().toString().equalsIgnoreCase("operator()"))
    			&& !(c.name().toString().equalsIgnoreCase("runAsync")) && path) {
	    	if (c.target() instanceof Field_c) {
	    		Field_c tempField = (Field_c)c.target();
	    		desVar = tempField.fieldInstance().name().toString();
	    		//System.out.println("Both details of the call var->field: " + tempField.name().toString() + ":" + c.name().toString());
	    		
	    		HashMap<String, LinkedList<EdgeRep>> varInfo = lastGraphInfo.peek();
	    		if (varInfo != null) {
	    			LinkedList<EdgeRep> ll = varInfo.get(tempField.fieldInstance().name().toString());
	    			if (ll != null && ll.size() > 0) {
		    			EdgeRep er = ll.getFirst();
		    			//System.out.println("The type is " + er.desName);
		    			desObj = er.desName;
	    			}
	    		}
	    		
	        	HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo2 = objInfo.get(temp1.lineNo);
	        	if (methodInfo2 != null) {
	        		HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo2.get(temp2.lineNo);
	        		if (placeInfo != null) {
	        			HashMap<String, ObjNode> objDetail = placeInfo.get(temp3.lineNo);
	        			if (objDetail != null) {
	        				ObjNode on = objDetail.get(desObj);
	        				if (on != null) {
	        					//System.out.println("The Type is " + on.objType);
	        					className = on.objType;
	        				}
	        			}
	        		}
	        	}
	    		
	    	}
	    	else {
	    		//System.out.println("Both details of the call: " + c.name().toString() + ":" + c.target().toString());
	    		
	    		String target = c.target().toString();
	    		
	    		
	    		if (c.target() instanceof Special_c) {
	    			target = "this";
	    		}
	    		
	    		desVar = target;
	    		
	    		HashMap<String, LinkedList<EdgeRep>> varInfo = null;
	    		if (lastGraphInfo.size() > 0) {
	    			varInfo = lastGraphInfo.peek();
	    		}
	    		if (varInfo != null) {
	    			LinkedList<EdgeRep> ll = varInfo.get(target);
	    			if (ll != null) {
	    				if (ll.size() > 0) {
			    			EdgeRep er = ll.getFirst();
			    			//System.out.println("The type is " + er.desName);
			    			desObj = er.desName;
	    				}
	    			}
	    		}
	    		
	    		if (currMethod.size() > 0) {
		        	HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo2 = objInfo.get(temp1.lineNo);
		        	if (methodInfo2 != null) {
		        		HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo2.get(temp2.lineNo);
		        		if (placeInfo != null) {
		        			HashMap<String, ObjNode> objDetail = placeInfo.get(temp3.lineNo);
		        			if (objDetail != null) {
		        				ObjNode on = objDetail.get(desObj);
		        				if (on != null) {
		        					//System.out.println("The Type is " + on.objType);
		        					className = on.objType;
		        				}
		        			}
		        		}
		        	}
	    		}
	    	}
	    	
	    	//System.out.println("the class and method name is: "+ className + ":"+ funName);
	    	int srcClass = 0;
	    	int srcMetPlace = 0;
	    	
	    	LinkedList<ClassInfo> fi = classDetails.get(className);
			if (fi != null) {
				Iterator it = fi.iterator();
				while (it.hasNext()) {
					ClassInfo ci = (ClassInfo)it.next();
					if (ci.name.equalsIgnoreCase(funName)) {
						srcClass = ci.classNo;
						srcMetPlace = ci.methodNo;
					}
				}
			}
	    	//System.out.println("The line numbers are CALL " + srcClass + ":" + srcMetPlace);
	    	/*[done for inter] HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
	    	if (setMethodInfo != null) {
	    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
	    		if (placeInfo != null) {
	    			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
	    			if (setDetails != null) {
	    				HashMap<String, HashSet<String>> rs = setDetails.get("RS");
	    				if (rs != null) {
	    					//Iterator it = rs.entrySet().iterator();
	    					//while (it.hasNext()) {
	    						//Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it.next();
	    						
	    						HashSet<String> set1 = rs.get(desObj);
	    						
	    						HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo1 = setInfo.get(srcClass);
	    						if (setMethodInfo1 != null) {
	    							HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo1 = setMethodInfo1.get(srcMetPlace);
	    							if (placeInfo1 != null) {
	    								HashMap<String, HashMap<String, HashSet<String>>> setDetails1 = placeInfo1.get(srcMetPlace);
	    								if (setDetails1 != null) {
	    									HashMap<String, HashSet<String>> rsSrc = setDetails1.get("RS");
	    									HashMap<String, HashSet<String>> crsSrc = setDetails1.get("CRS");
	    									
	    									HashSet<String> set2 = rsSrc.get("obj-this");
	    									HashSet<String> set3 = crsSrc.get("obj-this");
	    									
	    									if (set1 != null && set2 != null && set3 != null) {
	    									 set1.addAll(set2);
	    									 set1.addAll(set3);
	    									} 
	    								}
	    							}
	    						}

	    					//}
	    				}
	    			}
	    		}
	    	} [done for inter]*/
    	}
    	
    	/* the printer */
    	/*
    	if (path && (c.name().toString().equalsIgnoreCase("ShiftDown"))) {
	    	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo4 = setInfo.get(temp1.lineNo);
	    	if (setMethodInfo4 != null) {
	    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo4.get(temp2.lineNo);
	    		if (placeInfo != null) {
	    			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
	    			if (setDetails != null) {
	    				System.out.println();
			    		System.out.println("Method's Object Sets:");
			    		
			    		System.out.println("Read Set:");
			    		HashMap<String, HashSet<String>> creadSet = setDetails.get("RS");
			    		if (creadSet != null) {
			    			Iterator it3 = creadSet.entrySet().iterator();
			    			while (it3.hasNext()) {
			    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it3.next();
			        			System.out.println("Object: " + pair.getKey());
			        			HashSet<String> rs = (HashSet<String>)pair.getValue();
			        			
			        			if(rs != null) {
			        				Iterator it4 = rs.iterator();
			        				
			        				while (it4.hasNext()) {
			            				String str = (String)it4.next();
			            				System.out.print(" {" + str +"} ");
			            			}
			            			System.out.println("");
			        			}
			    			}
			    		}
	    			}
	    		}
	    	}
	    	System.out.println("::::::::::::::::::::::::::::::::after the function call::::::::::::::::::::::::::::::");
    	}
    	*/
    	/* the printer */
    	
    	/* Nobita code */ 
    	
        if (er.printInlinedCode(c)) {
            return;
        }

        if (c.isConstant()) {
            Type t = Types.baseType(c.type());
            if (isPrimitive(t) || t.isNull() || isString(t)) {
                er.prettyPrint(c.constantValue().toLit(tr.nodeFactory(), tr.typeSystem(), t, Position.COMPILER_GENERATED), tr);
                return;
            }
        }

        // XTENLANG-2680 invoke final methods as non-virtual call for optimization
        final MethodInstance mi = c.methodInstance();
        final Receiver target = c.target();
        final Type targetType = target.type();
        final ContainerType containerType = mi.container();
    	assert containerType.isClass();
    	// N.B. structs are implicitly final. all methods of final classes are final. invoke final methods as non-virtual call.
    	boolean invokeNativeAsNonVirtual = !Emitter.supportNativeMethodDecl || mi.flags().isStatic() || mi.flags().isFinal()
    	|| canBeNonVirtual(containerType.toClass().x10Def())
    	|| (targetType.isClass() && canBeNonVirtual(targetType.toClass().x10Def()))
    	;
        if (invokeNativeAsNonVirtual && er.printNativeMethodCall(c)) {
            return;
        }

        // Check for properties accessed using method syntax. They may have
        // @Native annotations too.
        if (mi.flags().isProperty() && mi.formalTypes().size() == 0 && mi.typeParameters().size() == 0) {
            X10FieldInstance fi = (X10FieldInstance) containerType.fieldNamed(mi.name());
            if (fi != null) {
                String pat2 = Emitter.getJavaImplForDef(fi.x10Def());
                if (pat2 != null) {
                    Map<String,Object> components = new HashMap<String,Object>();
                    int i = 0;
                    Object component;
                    component = target;
                    if (supportNumberedParameterForNative)
                    components.put(String.valueOf(i++), component);
                    // TODO need check
                    components.put(fi.x10Def().name().toString(), component);
                    er.dumpRegex("Native", components, tr, pat2);
                    return;
                }
            }
        }

        TypeSystem xts = tr.typeSystem();

        // When the target class is a generics , print a cast operation
        // explicitly.
        if (target instanceof TypeNode) {
            er.printType(targetType, BOX_PRIMITIVES);
        } else {
            // add a check that verifies if the target of the call is in place
            // 'here'
            // This is not needed for:

            if (!(target instanceof Special || target instanceof New)) {
                if (isSpecialType(targetType) && isBoxedType(containerType)) {
                	er.printBoxConversion(targetType);
                    w.write("(");
                    er.prettyPrint(target, tr);
                    w.write(")");
                } else
                if (xts.isParameterType(targetType)) {
                    // TODO:CAST
                    w.write("(");
                    w.write("(");
                    er.printType(containerType, PRINT_TYPE_PARAMS); // TODO
                                                                     // check
                    w.write(")");

                    w.write(X10_RTT_TYPES);
                    w.write(".conversion(");
                    new RuntimeTypeExpander(er, Types.baseType(containerType)).expand(tr);
                    w.write(",");

                    er.prettyPrint(target, tr);

                    w.write(")");

                    w.write(")");
                } else if ((useSelfDispatch && (mi.typeParameters().size() > 0 || hasParams(containerType) || isFormalTypeErased(tr.context().currentCode()))) ||
                           (target instanceof NullLit_c)) {
                    // TODO:CAST
                    w.write("(");
                    w.write("(");
                    er.printType(containerType, PRINT_TYPE_PARAMS);
                    w.write(")");
                    er.prettyPrint(target, tr);
                    w.write(")");
                } else {
                    er.prettyPrint(target, tr);
                }
            } else {
                er.prettyPrint(target, tr);
            }
        }

        w.write(".");

        // print type parameters
        List<Type> methodTypeParams = mi.typeParameters();
        if (methodTypeParams.size() > 0) {
            er.printMethodParams(methodTypeParams);
        }

        // print method name
        if (isMainMethod(mi) || mi.container().toClass().isJavaType()) {
            w.write(Emitter.mangleToJava(c.name().id()));
        } else {
            boolean invokeInterface = false;
            ContainerType st = mi.def().container().get();
            if (Emitter.isInterfaceOrFunctionType(xts, st)) {
            	invokeInterface = true;
            }

            boolean isDispatchMethod = false;
            if (useSelfDispatch) {
                if (xts.isInterfaceType(containerType)) {
                	// XTENLANG-2723 stop passing rtt to java raw class's methods (reverted in r21635)
                	if (containsTypeParam(mi.def().formalTypes()) /*&& !Emitter.isNativeRepedToJava(containerType)*/) {
                        isDispatchMethod = true;
                    }
                } else if (target instanceof ParExpr && ((ParExpr) target).expr() instanceof Closure_c) {
                    if (mi.formalTypes().size() != 0) {
                        isDispatchMethod = true;
                    }
                }
            }

            boolean instantiatesReturnType = false;
            List<MethodInstance> list = mi.implemented(tr.context());
            for (MethodInstance mj : list) {
                if (mj.container().typeEquals(containerType, tr.context()) && mj.def().returnType().get().isParameterType()) {
                    instantiatesReturnType = true;
                    break;
                }
            }

            MethodDef md = mi.def();
            boolean isParamReturnType = md.returnType().get().isParameterType() || instantiatesReturnType;

            if (c.nonVirtual()) {
                Name name = InlineHelper.makeSuperBridgeName(mi.container().toClass().def(), mi.name());
                List<MethodInstance> bridges = targetType.toClass().methodsNamed(name);
                assert (bridges.size()==1);
                md = bridges.get(0).def();
                isParamReturnType = false;
                w.write("/"+"*"+"non-virtual"+"*"+"/");
            }

            // call
            // XTENLANG-2993
            // for X10PrettyPrinterVisitor.exposeSpecialDispatcherThroughSpecialInterface
//            Type returnTypeForDispatcher = md.returnType().get();
            Type returnTypeForDispatcher = isPrimitive(mi.returnType()) && isPrimitiveGenericMethod(mi) ? mi.returnType() : md.returnType().get();
            // for X10PrettyPrinterVisitor.exposeSpecialDispatcherThroughSpecialInterface
//            boolean isSpecialReturnType = isSpecialType(md.returnType().get());
            boolean isSpecialReturnType = isPrimitive(mi.returnType()) && isPrimitiveGenericMethod(mi) ? true : isSpecialType(md.returnType().get());            
            er.printMethodName(md, invokeInterface, isDispatchMethod, generateSpecialDispatcher && !generateSpecialDispatcherNotUse, returnTypeForDispatcher, isSpecialReturnType, isParamReturnType);
        }

        // print the argument list
        w.write("(");
        w.begin(0);

        List<Type> typeParameters = mi.typeParameters();
        int argumentSize = c.arguments().size();
        printArgumentsForTypeParams(typeParameters, argumentSize == 0);

        boolean runAsync = false;
        if (Types.baseType(containerType).isRuntime()) {
            if (mi.signature().startsWith("runAsync")) {
                runAsync = true;
            }
        }

        List<Expr> exprs = c.arguments();
        MethodDef def = c.methodInstance().def();
        
        /* Nobita code */
        // for the inter procedural analysis
        HashMap<String, HashSet<String>> paraObjSet = new HashMap<String, HashSet<String>>();
        LinkedList<String> paraList = new LinkedList<String>();
        String uniqValue = "";
        boolean hasObjPara = false;
        boolean hasNullPara = false;
        
        //the below inter-procedural code is for the this variable
        if (!(c.name().toString().equalsIgnoreCase("Places")) && !(c.name().toString().equalsIgnoreCase("runAt")) && !(c.name().toString().equalsIgnoreCase("runAsync"))
    			&& !(c.name().toString().equalsIgnoreCase("wrapAtChecked")) && !(c.name().toString().equalsIgnoreCase("operator()"))
    			&& !(c.name().toString().equalsIgnoreCase("runAsync")) && path) {
        	objParaPresent = true;
        	
        	String varName = "";
        	if (c.target() instanceof Special_c) {
        		Special_c spec = (Special_c)c.target();
        		//TODO:: add the code to handle "super"
        		varName = spec.kind().toString();
        	}
        	else if (c.target() instanceof Local_c) {
        		Local_c local = (Local_c)c.target();
        		varName = local.name().toString();
        	}
        	else if (c.target() instanceof Field_c) {
        		Field_c fieldVar = (Field_c)c.target();
        		varName = fieldVar.fieldInstance().name().toString();
        	}
        	
        	paraList.addLast(varName);
        	
        	if (!paraObjSet.containsKey(varName)) {
				paraObjSet.put(varName, new HashSet<String>());
			}
        	
        	HashSet<String> set1 = paraObjSet.get(varName);
        	
        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
			if (methodInfo != null) {
				HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
				if (lineInfo != null) {
					HashMap<String, LinkedList<EdgeRep>> varInfo = lastGraphInfo.peek();//lineInfo.get(lineNo);
					if (varInfo != null) {
						LinkedList<EdgeRep> ller = varInfo.get(varName);
						if (ller != null) {
							Iterator it = ller.iterator();
							
							while (it.hasNext()) {
								EdgeRep er = (EdgeRep)it.next();
								if (!er.desName.equalsIgnoreCase("Obj-null")) {
									set1.add(er.desName);
									if (varInfo.containsKey(er.desName)) {
										paraObjCollectSet(set1, er.desName, varInfo);
									}
								}
							}
						}
					}
					
				}
			}
        }
        /* Nobita code */
        
        for (int i = 0; i < exprs.size(); ++i) {
            Expr e = exprs.get(i);
            
            /* Nobita code */
            if (!(c.name().toString().equalsIgnoreCase("Places")) && !(c.name().toString().equalsIgnoreCase("runAt")) && !(c.name().toString().equalsIgnoreCase("runAsync"))
        			&& !(c.name().toString().equalsIgnoreCase("wrapAtChecked")) && !(c.name().toString().equalsIgnoreCase("operator()"))
        			&& !(c.name().toString().equalsIgnoreCase("runAsync")) && path) {
            	
            	if (e.type().name() == null) {
            		uniqValue = uniqValue + "|" + "N";
            	}
            	else {
            		String varType = e.type().name().toString();
            		uniqValue = uniqValue + "|" + typeUniqueID.get(varType);
            	}
            }
            /* Nobita code */
            
            Type defType = def.formalTypes().get(i).get();
            if (runAsync && e instanceof Closure_c) {
                c.print(((Closure_c) e).methodContainer(mi), w, tr);
            }
            // else if (!er.isNoArgumentType(e)) {
            // new CastExpander(w, er, e).castTo(e.type(),
            // BOX_PRIMITIVES).expand();
            // }
            else {
                if (isPrimitive(e.type())) {
                    boolean forceBoxing = false;
                    if (!Emitter.canMangleMethodName(def)) {
                        // for methods with non-manglable names, we box argument
                        // if any of the implemented methods has argument of generic type
                        // in corresponding position
                        for (MethodInstance supermeth : c.methodInstance().implemented(tr.context())) {
                            if (isBoxedType(supermeth.def().formalTypes().get(i).get())) {
                                forceBoxing = true;
                                break;
                            }
                        }
                    }
                    // e.g) m((Integer) a) for m(T a)
                    boolean closeParen = false; // unbalanced closing parenthesis needed?
                    // N.B. @NativeRep'ed interface (e.g. Comparable) does not use dispatch method nor mangle method. primitives need to be boxed to allow instantiating type parameter.
                    if (isBoxedType(defType) || forceBoxing) {
                        // this can print something like '(int)' or 'UInt.$box' depending on the type
                        // we require the parentheses to be printed below 
                        er.printBoxConversion(e.type());
                        // e.g) m((int) a) for m(int a)
                    } else {
                        // TODO:CAST
                        w.write("(");
                        er.printType(e.type(), 0);
                        w.write(")");
                        if (e instanceof X10Call) {
                        } else if (e instanceof ClosureCall) {
                            ClosureCall cl = (ClosureCall) e;
                            Expr expr = cl.target();
                            // if (expr instanceof ParExpr) {
                            // expr = expr;
                            // }
                            if (!(expr instanceof Closure_c)
                                    && xts.isParameterType(cl.closureInstance().def().returnType().get())) {
                                // TODO:CAST
                                closeParen = er.printUnboxConversion(e.type());
                                w.write("(");
                                er.printType(e.type(), BOX_PRIMITIVES);
                                w.write(")");
                            }
                        }
                    }
                    w.write("("); // it is important to add parentheses here, as some call may have been issued above
                    c.print(e, w, tr);
                    w.write(")");
                    if (closeParen) w.write(")");
                    if (isMutableStruct(e.type())) {
                        w.write(".clone()");
                    }
                }
                // XTENLANG-1704
                else {
                    // TODO:CAST
                    w.write("(");

                    Type castType = mi.formalTypes().get(i);
                    w.write("(");
                    er.printType(castType, 0);
                    w.write(")");

                    if (isString(e.type()) && !isString(castType)) {
                        if (xts.isParameterType(castType)) {
                            w.write(X10_RTT_TYPES);
                            w.write(".conversion(");
                            new RuntimeTypeExpander(er, Types.baseType(castType)).expand(tr);
                            w.write(",");
                        }
                    }

                    w.write("(");
                    //Nobita Check this is the place for method params call made 
                    /* Nobita code */
                    //this code is for inter-procedural analysis
                    if (e.type().name() == null) {
                    	paraList.addLast("null");
                    	hasObjPara = true;
                    	hasNullPara = true;
                    }
                	if (!(c.name().toString().equalsIgnoreCase("Places")) && !(c.name().toString().equalsIgnoreCase("runAt")) && !(c.name().toString().equalsIgnoreCase("runAsync"))
                			&& !(c.name().toString().equalsIgnoreCase("wrapAtChecked")) && !(c.name().toString().equalsIgnoreCase("operator()"))
                			&& !(c.name().toString().equalsIgnoreCase("runAsync")) && path && e.type().name() != null) {
                		String varType = e.type().name().toString();
                		if((varType != null) && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
                        		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
                        		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
                        		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
                			
                			objParaPresent = true;
                			hasObjPara = true;
                			String varName = "";
                			if (e instanceof Local_c) {
                				Local_c local = (Local_c)e;
                				varName = local.name().toString();
                			}
                			else if (e instanceof Field_c) {
                				Field_c field = (Field_c)e;
                				varName = field.fieldInstance().name().toString();
                			}
                			
                			paraList.addLast(varName);
                			//System.out.println("Printing with rspect to index: " + paraList.get(0));
                			if (!paraObjSet.containsKey(varName)) {
                				paraObjSet.put(varName, new HashSet<String>());
                			}
                			HashSet<String> set1 = paraObjSet.get(varName);
                			
                			//these first 32 method info and line info are not required
                			HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
                			if (methodInfo != null) {
                				HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
                				if (lineInfo != null) {
                					HashMap<String, LinkedList<EdgeRep>> varInfo = lastGraphInfo.peek();//lineInfo.get(lineNo);
                					if (varInfo != null) {
                						LinkedList<EdgeRep> ller = varInfo.get(varName);
                						if (ller != null) {
                							Iterator it = ller.iterator();
                							
                							while (it.hasNext()) {
                								EdgeRep er = (EdgeRep)it.next();
                								if (!er.desName.equalsIgnoreCase("Obj-null")) {
                									set1.add(er.desName);
                									if (varInfo.containsKey(er.desName)) {
                										paraObjCollectSet(set1, er.desName, varInfo);
                									}
                								}
                							}
                						}
                					}
                					
                				}
                			}
                		}
                		
                	}
                	/* Nobita code */
                    c.print(e, w, tr);
                    w.write(")");
                    if (isMutableStruct(e.type())) {
                        w.write(".clone()");
                    }
                    w.write(")");
                }
            }

        	// XTENLANG-2723 stop passing rtt to java raw class's methods (reverted in r21635)
            if (useSelfDispatch && Emitter.isInterfaceOrFunctionType(xts, containerType) /*&& !Emitter.isNativeRepedToJava(containerType)*/ && Emitter.containsTypeParam(defType)) {
            	// if I is an interface and val i:I, t = type of the formal of method instance
            	// i.m(a) => i.m(a,t)
            	if (xts.isParameterType(containerType) || hasParams(containerType)) {
                    w.write(", ");
            		new RuntimeTypeExpander(er, c.methodInstance().formalTypes().get(i)).expand();
            	}
            }

            if (i != exprs.size() - 1) {
                w.write(", ");
            }
        }
        w.end();
        w.write(")");
        
        /* Nobita code */
        //this code is for inter-procedural analysis
        if (objParaPresent) {
        	fixSizeOfParam(paraList, paraObjSet, className, funName, lastGraphInfo.peek(), uniqValue, hasObjPara, hasNullPara);
        }
        
        //this is the code for reading back from the set
        if (!(c.name().toString().equalsIgnoreCase("Places")) && !(c.name().toString().equalsIgnoreCase("runAt")) && !(c.name().toString().equalsIgnoreCase("runAsync"))
    			&& !(c.name().toString().equalsIgnoreCase("wrapAtChecked")) && !(c.name().toString().equalsIgnoreCase("operator()"))
    			&& !(c.name().toString().equalsIgnoreCase("runAsync")) && path) {
        	
        	int srcClass = 0;
	    	int srcMetPlace = 0;
	    	
	    	ClassInfo ci = new ClassInfo();
	    	LinkedList<ClassInfo> llMethodPara = null;
	    	LinkedList<ClassInfo> llfi = classDetails.get(className);
	    	if (llfi != null) {
	    		Iterator it = llfi.iterator();
	    		while (it.hasNext()) {
	    			ci = (ClassInfo)it.next();
	    			if (uniqValue.equalsIgnoreCase("")) {
	    				if (ci.classifier.equalsIgnoreCase("method") && ci.name.equalsIgnoreCase(funName)) {
	    					srcClass = ci.classNo;
							srcMetPlace = ci.methodNo;
							llMethodPara = ci.methodPara;
							break;
	    				}
	    			}
	    			else {
	    				if (ci.classifier.equalsIgnoreCase("method") && ci.name.equalsIgnoreCase(funName) && ci.uniqueId.equalsIgnoreCase(uniqValue)) {
	    					srcClass = ci.classNo;
							srcMetPlace = ci.methodNo;
							llMethodPara = ci.methodPara;
							break;
	    				}
	    			}
	    		}
	    	}
	    	
	    	HashMap<String, LinkedList<EdgeRep>> varInfoCaller = lastGraphInfo.peek();
	    	
	    	HashMap<String, LinkedList<EdgeRep>> varInfoCallee = null;
	    	HashMap<String, HashMap<String, HashSet<String>>> setDetailsCaller = null;
	    	HashMap<String, HashMap<String, HashSet<String>>> setDetailsCallee = null;
	    	HashMap<String, ObjNode> objDetails = null;
	    	
	    	//varInfoCallee
	    	HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> methodInfo = graphInfoInitial.get(srcClass);
	    	//System.out.println("THe checker: " + ci.methodNoIn);
	    	if (methodInfo != null) {
	    		varInfoCallee = methodInfo.get(ci.methodNoIn);
	    	}
	    	
	    	//setDetailsCaller
	    	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
	    	if (setMethodInfo != null) {
	    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
	    		if (placeInfo != null) {
	    			setDetailsCaller = placeInfo.get(temp3.lineNo);
	    		}
	    	}
	    	
	    	//setDetailsCallee
	    	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo1 = setInfo.get(srcClass);
	    	if (setMethodInfo1 != null) {
	    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo1 = setMethodInfo1.get(srcMetPlace);
	    		if (placeInfo1 != null) {
	    			setDetailsCallee = placeInfo1.get(srcMetPlace);
	    		}
	    	}

	    	//Object Details
	    	HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo2 = objInfo.get(temp1.lineNo);
	    	if (methodInfo2 != null) {
	    		HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo2.get(temp2.lineNo);
	    		if (placeInfo != null) {
	    			objDetails = placeInfo.get(temp3.lineNo);
	    		}
	    	}
	    	
	    	//call the function here - for set updates
	    	//for the this object
	    	if (varInfoCaller != null && varInfoCallee != null && setDetailsCaller != null && setDetailsCallee != null) {
	    	
		    	LinkedList<EdgeRep> llerCallee = varInfoCallee.get("this");
		    	LinkedList<EdgeRep> llerCaller = varInfoCaller.get(desVar);
		    	if (llerCallee != null && llerCaller != null) {
		    		String callerObjName = "";
		    		String calleeObjName = "";
		    		
		    		Iterator it = llerCallee.iterator();
		    		while (it.hasNext()) {
		    			EdgeRep er = (EdgeRep)it.next();
		    			if (!er.desName.equalsIgnoreCase("Obj-null")) {
		    				calleeObjName = er.desName;
		    				break;
		    			}
		    		}
		    		
		    		Iterator it1 = llerCaller.iterator();
		    		while (it1.hasNext()) {
		    			EdgeRep er = (EdgeRep)it1.next();
		    			if (!er.desName.equalsIgnoreCase("Obj-null")) {
		    				callerObjName = er.desName;
		    				//call the function here
		    					fnCallSetUpdate(varInfoCaller, varInfoCallee, setDetailsCaller, setDetailsCallee, callerObjName, calleeObjName);
		    			}
		    		}
		    		
		    	}
		    	
		    	//now the call is from 1th parameter
		    	if (llMethodPara != null) {
		    		
		    		Stack<String> stc = new Stack<String>();
		    		
		    		Iterator it = llMethodPara.iterator();
		    		while (it.hasNext()) {
		    			ClassInfo ci1 = (ClassInfo)it.next();
		    			stc.push(ci1.name);
		    			
		    		}
		    		
		    		Stack<String> stcRev = new Stack<String>();
		    		while (stc.size() > 0) {
		    			stcRev.push(stc.pop());
		    		}
		    		//this is to popout this
		    		stcRev.pop();

			    	for (int i = 0; i < exprs.size(); ++i) {
			    		Expr e = exprs.get(i);
			   			
			   			if (e.type().name() == null) {
			   				stcRev.pop();
			   			}
		    			else {
		    				String varType = e.type().name().toString();
		    				if((varType != null) && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
	                        		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
	                        		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
	                        		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
		    					
		    					String desVarPar =  "";
		    					if (e instanceof Local_c) {
		    						Local_c localField = (Local_c)e;
		    						desVarPar =  localField.name().toString();
		    					}
		    					else if(e instanceof Field_c) {
		    						Field_c fieldVar = (Field_c)e;
		    						desVarPar = fieldVar.fieldInstance().name().toString();
		    					}
		    					
		    					
		    					String callerObjName = "";
		    		    		String calleeObjName = "";
		    					String stackTop = stcRev.pop();
		    					LinkedList<EdgeRep> llerCallee1 = varInfoCallee.get(stackTop);
		    					LinkedList<EdgeRep> llerCaller1 = varInfoCaller.get(desVarPar);
		    					if (llerCallee1 != null && llerCaller1 != null) {
		    						Iterator it2 = llerCallee1.iterator();
		    						while (it2.hasNext()) {
		    							EdgeRep er = (EdgeRep)it2.next();
		    							if (!er.desName.equalsIgnoreCase("Obj-null")) {
		    								calleeObjName = er.desName;
		    								break;
		    							}
		    						}
		    						
		    						Iterator it3 = llerCaller1.iterator();
		    						while (it3.hasNext()) {
		    							EdgeRep er = (EdgeRep)it3.next();
		    							if (!er.desName.equalsIgnoreCase("Obj-null")) {
		    			    				callerObjName = er.desName;
		    			    				//call the function here
		    			    					fnCallSetUpdate(varInfoCaller, varInfoCallee, setDetailsCaller, setDetailsCallee, callerObjName, calleeObjName);
		    			    			}
		    						}
		    					}
		    				}
		    			}			    			
			   		}

		    	}
		    	
	    	}
	    	
	    	//this to update the structure of the graph info
	    	HashMap<String, LinkedList<EdgeRep>> varInfoCallerCopy = null;
	    	if (lastGraphInfo.size() > 0) {
	    		varInfoCallerCopy = deepCopy(lastGraphInfo.pop());
	    	}
	    	
	    	//varInfoCallee
	    	boolean recursive = false;
	    	if (srcClass == temp1.lineNo && srcMetPlace == temp2.lineNo) {
	    		varInfoCallee = recVarInfo;
	    		recursive = true;
	    	}
	    	else {
		    	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo3 = graphInfo.get(srcClass);
		    	if (methodInfo3 != null) {
		    		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> placeInfo2 = methodInfo3.get(srcMetPlace);
		    		if (placeInfo2 != null) {
		    			varInfoCallee = placeInfo2.get(srcMetPlace);
		    		}
		    	}
	    	}
	    	
	    	//for this
	    	if (varInfoCaller != null && varInfoCallee != null && objDetails != null && varInfoCallerCopy != null) {
	    		LinkedList<EdgeRep> llerCallee = varInfoCallee.get("this");
		    	LinkedList<EdgeRep> llerCaller = varInfoCaller.get(desVar);
		    	if (llerCallee != null && llerCaller != null) {
		    		String callerObjName = "";
		    		String calleeObjName = "";
		    		
		    		Iterator it = llerCallee.iterator();
		    		while (it.hasNext()) {
		    			EdgeRep er = (EdgeRep)it.next();
		    			if (!er.desName.equalsIgnoreCase("Obj-null")) {
		    				calleeObjName = er.desName;
		    				break;
		    			}
		    		}
		    		
		    		Iterator it1 = llerCaller.iterator();
		    		while (it1.hasNext()) {
		    			EdgeRep er = (EdgeRep)it1.next();
		    			if (!er.desName.equalsIgnoreCase("Obj-null")) {
		    				callerObjName = er.desName;
		    				//call teh function here
		    				fnCallGraphUpdate(varInfoCaller, varInfoCallerCopy, varInfoCallee, objDetails, callerObjName, calleeObjName, lineNo, setDetailsCaller, recursive);
		    					
		    			}
		    		}
		    		
		    	}
		    	
		    	//now the call is from 1th parameter
		    	if (llMethodPara != null) {
		    		Stack<String> stc = new Stack<String>();
		    		
		    		Iterator it = llMethodPara.iterator();
		    		while (it.hasNext()) {
		    			ClassInfo ci1 = (ClassInfo)it.next();
		    			stc.push(ci1.name);
		    			
		    		}
		    		
		    		Stack<String> stcRev = new Stack<String>();
		    		while (stc.size() > 0) {
		    			stcRev.push(stc.pop());
		    		}
		    		//this is to popout this
		    		stcRev.pop();
		    		
		    		for (int i = 0; i < exprs.size(); ++i) {
		    			Expr e = exprs.get(i);
		    			
		    			if (e.type().name() == null) {
			   				stcRev.pop();
			   			}
		    			else {
		    				String varType = e.type().name().toString();
		    				if((varType != null) && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
	                        		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
	                        		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
	                        		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
		    					

		    					String desVarPar =  "";
		    					if (e instanceof Local_c) {
		    						Local_c localField = (Local_c)e;
		    						desVarPar =  localField.name().toString();
		    					}
		    					else if(e instanceof Field_c) {
		    						Field_c fieldVar = (Field_c)e;
		    						desVarPar = fieldVar.fieldInstance().name().toString();
		    					}
		    					
		    					String callerObjName = "";
		    		    		String calleeObjName = "";
		    					String stackTop = stcRev.pop();
		    					LinkedList<EdgeRep> llerCallee1 = varInfoCallee.get(stackTop);
		    					LinkedList<EdgeRep> llerCaller1 = varInfoCaller.get(desVarPar);
		    					if (llerCallee1 != null && llerCaller1 != null) {
		    						Iterator it2 = llerCallee1.iterator();
		    						while (it2.hasNext()) {
		    							EdgeRep er = (EdgeRep)it2.next();
		    							if (!er.desName.equalsIgnoreCase("Obj-null")) {
		    								calleeObjName = er.desName;
		    								break;
		    							}
		    						}
		    						
		    						Iterator it3 = llerCaller1.iterator();
		    						while (it3.hasNext()) {
		    							EdgeRep er = (EdgeRep)it3.next();
		    							if (!er.desName.equalsIgnoreCase("Obj-null")) {
		    			    				callerObjName = er.desName;
		    			    				//call the function here
		    			    				fnCallGraphUpdate(varInfoCaller, varInfoCallerCopy, varInfoCallee, objDetails, callerObjName, calleeObjName, lineNo, setDetailsCaller, recursive);
		    			    			}
		    						}
		    					}
		    				}
		    				
		    			}
		    		}
		    	}
	    	}
	    	
	    	lastGraphInfo.push(varInfoCallerCopy);
	    	
        }
        /* Nobita code */
    }

    private void printArgumentsForTypeParams(List<? extends Type> typeParameters, boolean isLast) {
        for (Iterator<? extends Type> i = typeParameters.iterator(); i.hasNext();) {
            final Type at = i.next();
            new RuntimeTypeExpander(er, at).expand(tr);
            if (i.hasNext() || !isLast) {
                w.write(", ");
            }
        }
    }

    private void printArgumentsForTypeParamsPreComma(List<? extends Type> typeParameters, boolean isFirst) {
        if (typeParameters == null) return;
        for (Type at : typeParameters) {
            if (isFirst) {
                isFirst = false;
            } else {
                w.write(", ");
            }
            new RuntimeTypeExpander(er, at).expand(tr);            
        }
    }

    private boolean isMainMethod(MethodInstance mi) {
        return HierarchyUtils.isMainMethod(mi, tr.context());
    }

    @Override
    public void visit(X10Cast_c c) {
        TypeNode tn = c.castType();
        assert tn instanceof CanonicalTypeNode;

        Expr expr = c.expr();
        Type exprType = expr.type();

        switch (c.conversionType()) {
        case CHECKED:
        case PRIMITIVE:
        case SUBTYPE:
        case UNCHECKED:
            if (tn instanceof X10CanonicalTypeNode) {
                X10CanonicalTypeNode castTN = (X10CanonicalTypeNode) tn;

                Type castType = Types.baseType(castTN.type());
                Expander castTE = new TypeExpander(er, castType, PRINT_TYPE_PARAMS);
                Expander castRE = new RuntimeTypeExpander(er, castType);
                Expander exprRE = new RuntimeTypeExpander(er, exprType);

                TypeSystem xts = exprType.typeSystem();

                // Note: constraint checking should be desugared when compiling
                // without NO_CHECKS flag

                // e.g. any as Int (any:Any), t as Int (t:T)
                if (isBoxedType(exprType) && xts.isStruct(castType)) {
                	// N.B. castType.isUnsignedNumeric() must be before isPrimitive(castType)
                	// since Int and UInt are @NativeRep'ed to the same Java primive int.
                	if (castType.isUnsignedNumeric()) {
                        w.write(X10_RTT_TYPES + ".as" + castType.name().toString());
                        w.write("(");
                        c.printSubExpr(expr, w, tr);
                        w.write(",");
                    	exprRE.expand();
                        w.write(")");
                    }
                    else if (isPrimitive(castType)) {
                        w.write(X10_RTT_TYPES + ".as");
                        er.printType(castType, NO_QUALIFIER);
                        w.write("(");
                        c.printSubExpr(expr, w, tr);
                        w.write(",");
                    	exprRE.expand();
                        w.write(")");
                    }
                    else {
                        w.write("(");
                        w.write("(");
                        er.printType(castType, 0);
                        w.write(")");
                        w.write(X10_RTT_TYPES + ".asStruct(");
                        castRE.expand();
                        w.write(",");
                        c.printSubExpr(expr, w, tr);
                        w.write(")");
                        w.write(")");
                    }
                } else if (isPrimitive(castType)) {
                    w.begin(0);
                    // for the case the method is a dispatch method and that
                    // returns Object.
                    // e.g. (Boolean) m(a)
                    if (castType.typeEquals(Types.baseType(exprType), tr.context())) {
                        boolean closeParen = false;
                        if (expr instanceof X10Call) {
                            X10Call call = (X10Call)expr;
                            MethodInstance mi = call.methodInstance();
                            if (!isPrimitiveGenericMethod(mi) && 
                                ((isBoxedType(mi.def().returnType().get()) && !er.isInlinedCall(call)) || Emitter.isDispatcher(mi)) )
                                closeParen = er.printUnboxConversion(castType);
                        } else if (expr instanceof ClosureCall) {
                            ClosureCall call = (ClosureCall)expr;
                            if (isBoxedType(call.closureInstance().def().returnType().get()))
                                closeParen = er.printUnboxConversion(castType);
                        }
                        c.printSubExpr(expr, w, tr);
                        if (closeParen) w.write(")");
                    } else {
                        w.write("("); // put "(Type) expr" in parentheses.
                        w.write("(");
                        castTE.expand(tr);
                        w.write(")");
                        // e.g. d as Int (d:Double) -> (int)(double)(Double) d
                        if (isPrimitive(exprType)) {
                            w.write(" ");
                            w.write("(");
                            er.printType(exprType, 0);
                            w.write(")");
                            w.write(" ");
                            if (!(expr instanceof Unary || expr instanceof Lit) && (expr instanceof X10Call)) {
                                w.write("(");
                                er.printType(exprType, BOX_PRIMITIVES);
                                w.write(")");
                            }
                        }
                        // TODO pretty print
                        w.allowBreak(2);
                        // HACK: (java.lang.Integer) -1
                        // doesn't parse correctly, but
                        // (java.lang.Integer) (-1)
                        // does
                        boolean needParan = expr instanceof Unary || expr instanceof Lit
                                || expr instanceof Conditional_c;
                        if (needParan) w.write("(");
                        c.printSubExpr(expr, w, tr);
                        if (needParan) w.write(")");
                        w.write(")");
                    }
                    w.end();
                } else if (exprType.isSubtype(castType, tr.context())) {
                    w.begin(0);
                    w.write("("); // put "(Type) expr" in parentheses.
                    w.write("(");
                    castTE.expand(tr);
                    w.write(")");

                    if (castType.isClass()) {
                        X10ClassType ct = castType.toClass();
                        if (ct.hasParams()) {
                            boolean castToRawType = false;
                            for (Variance variance : ct.x10Def().variances()) {
                                if (variance != Variance.INVARIANT) {
                                    castToRawType = true;
                                    break;
                                }
                            }
                            if (castToRawType) {
                                // cast to raw type
                                // e.g. for covariant class C[+T]{} and
                                // C[Object] v = new C[String](),
                                // it generates class C<T>{} and C<Object> v =
                                // (C<Object>) (C) (new C<String>()).
                                w.write("(");
                                er.printType(castType, 0);
                                w.write(")");
                            }
                        }
                    }

                    // TODO pretty print
                    w.allowBreak(2);

                    boolean closeParen = false; // provide extra closing parenthesis
                    if (isString(exprType) && !isString(castType)) {
                        if (xts.isParameterType(castType)) {
                            w.write(X10_RTT_TYPES);
                            w.write(".conversion(");
                            castRE.expand();
                            w.write(",");
                        } else {
                            // box only if converting to function type
                            if (xts.isFunctionType(castType)) {
                            	er.printBoxConversion(exprType);
                            }
                            w.write("(");
                        }
                        closeParen = true;
                    } else if (needExplicitBoxing(exprType) && isBoxedType(castType)) {
                    	er.printBoxConversion(exprType);
                    	w.write("(");
                        closeParen = true;
                    }
                        
                    boolean needParen = expr instanceof Unary
                            || expr instanceof Lit
                            || expr instanceof Conditional_c;
                    if (needParen) w.write("(");
                    c.printSubExpr(expr, w, tr);
                    if (needParen) w.write(")");

                    if (closeParen)
                        w.write(")");

                    w.write(")");
                    w.end();
                } else {
                    // SYNOPSIS: (#0) #1
                    //  -> Types.<#0>cast(#1,#2)   #0=type #1=expr #2=runtime type
                    //  -> Types.<#0>castConversion(#1,#2)   #0=type #1=expr #2=runtime type
                    w.write(X10_RTT_TYPES + ".<");
                    er.prettyPrint(castTE, tr);
                    boolean convert = xts.isParameterType(exprType) || !xts.isAny(Types.baseType(exprType)) && xts.isParameterType(castType) || isString(castType);
                    w.write("> cast" + (convert ? "Conversion" : "") + "(");
                    boolean closeParen = false;
                    if (needExplicitBoxing(exprType) && isBoxedType(castType)) {
                        er.printBoxConversion(exprType);
                        w.write("(");
                        closeParen = true;
                    }
                    er.prettyPrint(expr, tr);
                    if (closeParen) w.write(")");
                    w.write(",");
                    er.prettyPrint(castRE, tr);
                    w.write(")");
                }
            } else {
                throw new InternalCompilerError("Ambiguous TypeNode survived type-checking.", tn.position());
            }
            break;
        case BOXING:
            er.printBoxConversion(c.type());
            w.write("(");
            er.prettyPrint(c.expr(), tr);
            w.write(")");
            break;
        case UNBOXING:
            boolean closeParen;
            closeParen = er.printUnboxConversion(c.type());
            er.prettyPrint(c.expr(), tr);
            if (closeParen) w.write(")");
            break;
        case UNKNOWN_IMPLICIT_CONVERSION:
            throw new InternalCompilerError("Unknown implicit conversion type after type-checking.", c.position());
        case UNKNOWN_CONVERSION:
            throw new InternalCompilerError("Unknown conversion type after type-checking.", c.position());
        }
    }

    @Override
    public void visit(Conditional_c n) {
        n.translate(w, tr);
    }

    /*
     * Field access -- this includes only access of fields for read;
     * see visit(FieldAssign_c) for write access.
     */
    @Override
    public void visit(Field_c n) {
    	

        Receiver target = n.target();
        Type targetType = target.type();

        TypeSystem xts = targetType.typeSystem();
        X10FieldInstance fi = (X10FieldInstance) n.fieldInstance();

        // print native field access
        String pat = Emitter.getJavaImplForDef(fi.x10Def());
        if (pat != null) {
            Map<String,Object> components = new HashMap<String,Object>();
            int i = 0;
            Object component;
            component = target;
            if (supportNumberedParameterForNative)
            components.put(String.valueOf(i++), component);
            components.put("this", component);
            // TODO is this needed?
//            components.put(fi.x10Def().name().toString(), component);
            er.dumpRegex("Native", components, tr, pat);
            return;
        }

        if (target instanceof TypeNode) {
            TypeNode tn = (TypeNode) target;
            if (targetType.isParameterType()) {
                // Rewrite to the class declaring the field.
                FieldDef fd = fi.def();
                targetType = Types.get(fd.container());
                target = tn.typeRef(fd.container());
                n = (Field_c) n.target(target);
            }
        }

        // static access
        if (target instanceof TypeNode) {
            er.printType(targetType, 0);
            w.write(".");
            w.write(Emitter.mangleToJava(n.name().id()));
        } else {
            assert target instanceof Expr;
            boolean closeParen = false;
            Type fieldType = n.fieldInstance().def().type().get();
            if (xts.isStruct(target.type()) && !isBoxedType(n.type()) && isBoxedType(fieldType)) {
                closeParen = er.printUnboxConversion(n.type());
            }
            w.begin(0);
            if (!n.isTargetImplicit()) {
                if ((target instanceof NullLit_c) ||
                    (!(target instanceof Special || target instanceof New) && (xts.isParameterType(targetType) || hasParams(fi.container()) || isFormalTypeErased(tr.context().currentCode())))) {
                    // TODO:CAST
                    w.write("(");
                    w.write("(");
                    er.printType(fi.container(), PRINT_TYPE_PARAMS);
                    w.write(")");
                    n.printSubExpr((Expr) target, w, tr);
                    w.write(")");
                } else {
                    n.printSubExpr((Expr) target, w, tr);
                }
                w.write(".");
            }
            else {
                tr.print(n, target, w);
                w.write(".");
            }
            tr.print(n, n.name(), w);
            if (closeParen) w.write(")");
            w.end();
        }
    }

    @Override
    public void visit(X10Instanceof_c c) {
        // Note: constraint checking should be desugared when compiling without
        // NO_CHECKS flag

        Type t = c.compareType().type();

        // XTENLANG-1102
        if (t.isClass()) {
            X10ClassType ct = t.toClass();
            X10ClassDef cd = ct.x10Def();
            String pat = Emitter.getJavaRTTRep(cd);

            if (t instanceof FunctionType) {
                FunctionType ft = (FunctionType) t;
                List<Type> args = ft.argumentTypes();
                Type ret = ft.returnType();
                if (ret.isVoid()) {
                    w.write(X10_VOIDFUN_CLASS_PREFIX);
                } else {
                    w.write(X10_FUN_CLASS_PREFIX);
                }
                w.write("_" + ft.typeParameters().size());
                w.write("_" + args.size());
                w.write("." + RTT_NAME);
            } else if (pat == null && !ct.isJavaType() && Emitter.getJavaRep(cd) == null && ct.isGloballyAccessible()
                    && cd.typeParameters().size() != 0) {
            	String rttString = RuntimeTypeExpander.getRTT(Emitter.mangleQName(cd.fullName()).toString(), RuntimeTypeExpander.hasConflictingField(ct, tr));
            	w.write(rttString);
            } else {
                new RuntimeTypeExpander(er, t).expand(tr);
            }
        } else {
            new RuntimeTypeExpander(er, t).expand(tr);
        }

        w.write(".");
        w.write("isInstance(");

        Type exprType = Types.baseType(c.expr().type());
        boolean needParen = false;
        if (needExplicitBoxing(exprType)) {
        	er.printBoxConversion(exprType);
        	w.write("(");
        	needParen = true;
        }

        tr.print(c, c.expr(), w);

        if (needParen) {
        	w.write(")");
        }

        if (t.isClass()) {
            X10ClassType ct = t.toClass();
            X10ClassDef cd = ct.x10Def();
            String pat = Emitter.getJavaRTTRep(cd);

            if (pat == null && Emitter.getJavaRep(cd) == null && ct.typeArguments() != null) {
                for (int i = 0; i < ct.typeArguments().size(); i++) {
                    w.write(", ");
                    new RuntimeTypeExpander(er, ct.typeArguments().get(i)).expand(tr);
                }
            }
        }
        w.write(")");
    }

    @Override
    public void visit(Lit_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(IntLit_c n) {
        String val = null;
        switch (n.kind()) {
        case BYTE:
        case UBYTE:
            val = "((byte) " + Byte.toString((byte) n.value()) + ")";
            break;
        case SHORT:
        case USHORT:
            val = "((short) " + Short.toString((short) n.value()) + ")";
            break;
        case INT:
        case UINT:
            val = Integer.toString((int) n.value());
            break;
        case LONG:
        case ULONG:
            val = Long.toString(n.value()) + "L";
            break;
        // default: // Int, Short, Byte
        // if (n.value() >= 0x80000000L)
        // val = "0x" + Long.toHexString(n.value());
        // else
        // val = Long.toString((int) n.value());
        }
        if (!n.type().isLongOrLess()) {
            assert (Types.baseType(n.type()).isAny());
            w.write("(");
            er.printBoxConversion(n.constantValue().getLitType(tr.typeSystem()));
            w.write("(");
        }
        w.write(val);
        if (!n.type().isLongOrLess()) {
            w.write("))");
        }
    }

    @Override
    public void visit(StringLit_c n) {
        w.write("\"");
        w.write(StringUtil.escape(n.value()));
        w.write("\"");
        // N.B. removed it since now we pass captured environment explicitly,
        // therefore the workaround is no longer needed.
        // w.write(".toString()"); // workaround for XTENLANG-2006.
    }

    @Override
    public void visit(Local_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(X10New_c c) {
    	
    	/* Nobita code */
    	if (atCall) {
    		atCall = false;
    		lineNo++;
    		//System.out.println("the line number at call[NEW]: " + lineNo);
    	
    		if ((currClass.size() > 0) && (currMethod.size() > 0)) {
    			
    			//done before the closure call
    			//getting the class and the method
	        	VarWithLineNo temp1 = currClass.peek();
	        	VarWithLineNo temp2 = currMethod.peek();
	        	VarWithLineNo temp3 = currPlace.peek();
	        	
	        	//the graph
	        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
	        	if (methodInfo != null) {
	        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
	        		if (lineInfo != null) {
	        			if (lastGraphInfo.size() > 0) {
	        				lineInfo.put(lineNo, lastGraphInfo.peek());
	        			}
	        		}
	        	}
	        	
	        	//the set
	        	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
	        	if (setMethodInfo != null) {
	        		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
	        		if (placeInfo != null) {
	        			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
	        			if (setDetails != null) {
	        				HashMap<String, HashMap<String, HashSet<String>>> setBackUp = deepCopySetIf(setDetails);
		    				if (setBackUp != null) {
		    					
		    					HashMap<String, HashSet<String>> ovSet = setBackUp.get("OVS");
		    					if (ovSet != null) {
		    						ovSet.replace("global-ovs", new HashSet<String>());
		    					}
		    					placeInfo.put(lineNo, setBackUp);
		    				}
	        			}
	        		}
	        	}
	        	
	        	//the object
				HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo2 = objInfo.get(temp1.lineNo);
				if (methodInfo2 != null) {
					HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo2.get(temp2.lineNo);
					if (placeInfo != null) {
						HashMap<String, ObjNode> objDetail = placeInfo.get(temp3.lineNo);
						if (objDetail != null) {
							HashMap<String, ObjNode> objBackUp = deepCopyObject(objDetail);
							placeInfo.put(lineNo, objBackUp);
						}
					}
				}
	        	
	        	//creating the place tree
				HashMap<Integer, PlaceNode> methodInfoPlace = placeTree.get(temp1.lineNo);
				if (methodInfoPlace != null) {
					PlaceNode pn = methodInfoPlace.get(temp2.lineNo);
					PlaceNode pn1 = new PlaceNode(c.objectType().nameString());
					pn.addChild(pn1);
				}
				
				//for the closure details
				if (!closureInfo.containsKey(c.objectType().nameString())) {
					ClosureDetails cd = new ClosureDetails(c.objectType().nameString(), temp1.lineNo, temp2.lineNo, lineNo);
					closureInfo.put(c.objectType().nameString(), cd);
				}
				
				
				//done after the closure call [may be for reading back the sets]
				ClosureDetails cd1 = closureInfo.get(c.objectType().nameString());
				//System.out.println("The closure object name " + c.objectType().nameString());
				if (cd1.toClass > 0) {
					//System.out.println("The line numbers: " + cd1.fromClass +":"+ cd1.fromMethod +":"+ cd1.fromplace
							//+":"+cd1.toClass+":"+cd1.toMethod+":"+cd1.toplace);
					HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo1 = setInfo.get(temp1.lineNo);
					//System.out.println("first line: " + temp1.lineNo);
					if (setMethodInfo1 != null) {
						HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo1 = setMethodInfo1.get(temp2.lineNo);
						//System.out.println("second line: " + temp2.lineNo);
						if (placeInfo1 != null) {
							HashMap<String, HashMap<String, HashSet<String>>> setDetails1 = placeInfo1.get(lineNo);
							//System.out.println("third line: " + cd1.fromplace);
							if (setDetails1 != null) {
								HashMap<String, HashSet<String>> crsMethod = setDetails1.get("CRS");
								
								//for the ovs
								HashMap<String, HashSet<String>> ovsMethod = setDetails1.get("OVS");
								
								
								if (crsMethod != null && ovsMethod != null) {
									HashSet<String> set11 = ovsMethod.get("global-ovs");
									int number = 0;
									Iterator it1 = crsMethod.entrySet().iterator();
									while (it1.hasNext()) {
										Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it1.next();
										
										HashSet<String> set1 = crsMethod.get(phase3.getKey());
										
										HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo2 = setInfo.get(cd1.toClass);
										if (setMethodInfo2 != null) {
											HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo2 = setMethodInfo2.get(cd1.toMethod);
											if (placeInfo2 != null) {
												HashMap<String, HashMap<String, HashSet<String>>> setDetails2 = placeInfo2.get(cd1.toplace);
												if (setDetails2 != null) {
													HashMap<String, HashSet<String>> rs = setDetails2.get("RS");
													HashMap<String, HashSet<String>> crs = setDetails2.get("CRS");
													
													//System.out.println("The phase values: " + phase3.getKey());
													HashSet<String> set2 = null;
													HashSet<String> set3 = null;
													if (rs != null && crs != null) {
														set2 = rs.get(phase3.getKey());
														set3 = crs.get(phase3.getKey());
													}
													
													if (set1 != null && set2 != null && set3 != null) {
														set1.addAll(set2);
														set1.addAll(set3);
													}
													
													//for ovs
													HashMap<String, HashSet<String>> ovs = setDetails2.get("OVS");
													if (ovs != null) {
														HashSet<String> set12 = ovs.get("global-ovs");
														if (set12 != null && set11 != null) {
															set11.addAll(set12);
														}
													}
													
													//
													number = number + set1.size() + set11.size();	
												}
											}
										}
									}
									
									//code to continue for the next iteration
									//System.out.println("THE COMPARISION: " + number + " THE SECOND: " + cd1.countCRSWS);
									if (number != cd1.countCRSWS) {
										nextIteration = true;
										cd1.countCRSWS = number;
									}
								}
							}
						}
					}
					
					
					//the few calculations after pass one
					HashSet<String> set2 = null;
					HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo2 = setInfo.get(temp1.lineNo);
					if (setMethodInfo2 != null) {
						HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo1 = setMethodInfo2.get(temp2.lineNo);
						if (placeInfo1 != null) {
							HashMap<String, HashMap<String, HashSet<String>>> closureSet = placeInfo1.get(lineNo);
							if (closureSet != null) {
								HashMap<String, HashSet<String>> closureOVS = closureSet.get("OVS");
								
								if (closureOVS != null) {
									set2 = closureOVS.get("global-ovs");
								}
							}
						}
					}
					
					//now doing operations for the objects
					HashMap<String, ObjNode> objDetail = null;
					HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo3 = objInfo.get(temp1.lineNo);
					if (methodInfo2 != null) {
						HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo3.get(temp2.lineNo);
						if (placeInfo != null) {
							 objDetail = placeInfo.get(lineNo);
						}
					}
					
					//for the graph
					HashMap<String, LinkedList<EdgeRep>> varInfo = null;
		        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo4 = graphInfo.get(temp1.lineNo);
		        	if (methodInfo != null) {
		        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo4.get(temp2.lineNo);
		        		if (lineInfo != null) {
 		        			varInfo = lineInfo.get(lineNo);
		        		}
		        	}
		        	
		        	//the printer
		        	/*
		        	if (set2 != null) {
		        		System.out.println("PRINTING THE SET");
						Iterator it = set2.iterator();
						while (it.hasNext()) {
							String str = (String)it.next();
							System.out.println("THE SET IS: " + str);
						}
		        		
					}
		        	*/
		        	//thhe printer
		        	
		        	if (set2 != null && objDetail != null && varInfo != null) {
		        		forOVS(set2, objDetail, varInfo);
		        	}
					
					
				}
				
				/* the printer */
				/*
				System.out.println();
				System.out.println(":::::::::::::::::::sets and objects after the " + c.objectType().nameString() +"::::::::::::::::::::::::::::::");
				
				
				HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo4 = setInfo.get(temp1.lineNo);
				if (setMethodInfo4 != null) {
					HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo4.get(temp2.lineNo);
					if (placeInfo != null) {
						HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(lineNo);
						if (setDetails != null) {
							System.out.println();
				    		System.out.println("Method's Object Sets:");
				    		
				    		System.out.println("Cumulative Read Set:");
				    		HashMap<String, HashSet<String>> creadSet = setDetails.get("CRS");
				    		if (creadSet != null) {
				    			Iterator it3 = creadSet.entrySet().iterator();
				    			while (it3.hasNext()) {
				    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it3.next();
				        			System.out.println("Object: " + pair.getKey());
				        			HashSet<String> rs = (HashSet<String>)pair.getValue();
				        			
				        			if(rs != null) {
				        				Iterator it4 = rs.iterator();
				        				
				        				while (it4.hasNext()) {
				            				String str = (String)it4.next();
				            				System.out.print(" {" + str +"} ");
				            			}
				            			System.out.println("");
				        			}
				    			}
				    		}
				    		
				    		System.out.println("");
				    		System.out.println("OV Set:");
				    		HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
				    		if (ovSet != null) {
				    			Iterator it3 = ovSet.entrySet().iterator();
				    			while (it3.hasNext()) {
				    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it3.next();
				        			System.out.println("Object: " + pair.getKey());
				        			HashSet<String> ovs = (HashSet<String>)pair.getValue();
				        			
				        			if(ovs != null) {
				        				Iterator it4 = ovs.iterator();
				        				
				        				while (it4.hasNext()) {
				            				String str = (String)it4.next();
				            				System.out.print(" {" + str +"} ");
				            			}
				            			System.out.println("");
				        			}
				    			}
				    		}
				    		
				    		
						}
					}
				}
				
				
				System.out.println( );
				System.out.println("The Object details: ");
				HashMap<String, ObjNode> objDetail = null;
				HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo3 = objInfo.get(temp1.lineNo);
				if (methodInfo2 != null) {
					HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo3.get(temp2.lineNo);
					if (placeInfo != null) {
						HashMap<String, ObjNode> objDetail1 = placeInfo.get(lineNo);
						 
						 if (objDetail1 != null) {
							 Iterator it = objDetail1.entrySet().iterator();
							 while(it.hasNext()) {
								 Map.Entry<String, ObjNode> phase3 = (Map.Entry<String, ObjNode>)it.next();
								 
								 System.out.println("For object: " + phase3.getKey());
								 ObjNode on = (ObjNode)phase3.getValue();
								 System.out.println("Name: " + on.name + " TYPE:" + on.objType + " COUNTER: " + on.counter);
							 }
						 }
						 
					}
				}
				System.out.println(":::::::::::::::::::sets and objects after the " + c.objectType().nameString() +"::::::::::::::::::::::::::::::");
				System.out.println();
				*/
				/* the printer */
				
				//for the up-coming stmt the new group of sets
				HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo3 = setInfo.get(temp1.lineNo);
	        	if (setMethodInfo != null) {
	        		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo3.get(temp2.lineNo);
	        		if (placeInfo != null) {
	        			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
	        			if (setDetails != null) {
	        				HashMap<String, HashMap<String, HashSet<String>>> setBackUp = copySetToSamePlace(setDetails);
		    				if (setBackUp != null) {
		    					placeInfo.put(temp3.lineNo, setBackUp);
		    				}
	        			}
	        		}
	        	}
	        	
    		}	
    		
    		//for the pass two
    		//System.out.println("Arun: printing iteration count & supp: " + iterationCount + ":" + iterationCountSupp);
    		if (iterationCount == iterationCountSupp && (currClass.size() > 0) && (currMethod.size() > 0)) {
    			printConstructorArgumentList_optimizer(c, c.objectType().nameString(), lineNo);
    			
    			//the printer
    			/*
    			HashMap <String, String> varClosure = closureVar.get(c.objectType().nameString());
    			if (varClosure != null) {
    				System.out.println("The variable list");
    				Iterator it = varClosure.entrySet().iterator();
    				while (it.hasNext()) {
    					Map.Entry<String, String> phase3 = (Map.Entry<String, String>)it.next();
    					System.out.println("The variable: " + phase3.getKey() + " its status/object name: " + phase3.getValue());
    				}
    			}
    			System.out.println("");
    			
    			
    			HashMap<String, ForClosureObject> objClosure = closureObj.get(c.objectType().nameString());
    			if (objClosure != null) {
    				System.out.println("The Object list");
    				Iterator it = objClosure.entrySet().iterator();
    				while (it.hasNext()) {
    					Map.Entry<String, ForClosureObject> phase3 = (Map.Entry<String, ForClosureObject>)it.next();
    					
    					System.out.println("Printing closure details for object: " + phase3.getKey());
    					ForClosureObject fco = (ForClosureObject)phase3.getValue();
    					System.out.println("It's VariableName: " + fco.varName + " it's ambiguity: " + fco.ambiguity);
    					System.out.println("");
    					
    					LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
    					Iterator it1 = llFCOF.iterator();
    					while (it1.hasNext()) {
    						ForClosureObjectField fcfo = (ForClosureObjectField)it1.next();
    						System.out.println("FieldName: " + fcfo.fieldName + " TempFieldName: " + fcfo.tempStoredName + " FieldType: " + fcfo.fieldType);
    						System.out.println("");
    					}
    					
    				}
    			}
    			
    			*/
    			//the printer
    		}
    	}
    	//String temp = c.objectType().nameString();
    	//char z = temp.charAt(1);
    	//System.out.println("The x10 new type: " + c.objectType().nameString());
    	/* Nobita code */
    	
    	
        Type type = c.objectType().type();
        X10ConstructorInstance mi = c.constructorInstance();

        if (er.printNativeNew(c, mi)) return;
        
        if (isSplittable(type) && !type.fullName().toString().startsWith("java.")) {

            printAllocationCall(type, mi.container().toClass().typeArguments());

            w.write(".");

            w.write(CONSTRUCTOR_METHOD_NAME(type.toClass().def()));
            printConstructorArgumentList(c, c, c.constructorInstance(), null, false);

            return;
        }

        if (c.qualifier() != null) {
            tr.print(c, c.qualifier(), w);
            w.write(".");
        }

        w.write("new ");

        if (c.qualifier() == null) {
            er.printType(type, PRINT_TYPE_PARAMS | NO_VARIANCE);
        } else {
            er.printType(type, PRINT_TYPE_PARAMS | NO_VARIANCE | NO_QUALIFIER);
        }
        
        /*Nobita modified code */
        if (iterationCount > iterationCountSupp && changeConstruct && (currClass.size() > 0) && (currMethod.size() > 0)) {
        	changeConstruct = false;
        	savedObj = new Stack<String>();
        	printConstructorArgumentList_pp1(c, c, mi, type, true, c.objectType().nameString());
        	savedObj = new Stack<String>();
        }
        else {
        	printConstructorArgumentList(c, c, mi, type, true);
        }
        /*Nobita modified code */
        if (c.body() != null) {
            w.write("{");
            tr.print(c, c.body(), w);
            w.write("}");
        }
    }

    private void printExtraArgments(X10ConstructorInstance mi) {
        ConstructorDef md = mi.def();
        if (md instanceof X10ConstructorDef) {
            int cid = getConstructorId((X10ConstructorDef) md);
            if (cid != -1) {
                String extraTypeName = getExtraTypeName((X10ConstructorDef) md);
                w.write(", (");
                // print as qualified name
                er.printType(md.container().get(), 0); w.write(".");
                w.write(extraTypeName + ") null");
            }
        }
    }

    @Override
    public void visit(Special_c n) {
    	
    	/* Nobita code */
    	//System.out.println("THE SPECIAL VARIABLE: " + n.kind().name().toString() + ":" + n.kind().toString());
    	/* Nobita code */
    	
        X10CContext_c c = (X10CContext_c) tr.context();
        if (n.kind() == Special.THIS && c.getOverideNameForThis() != null) {
            w.write(c.getOverideNameForThis());
            return;
        }
        /*
         * The InnerClassRemover will have replaced the
         */
        if (c.inAnonObjectScope() && n.kind() == Special.THIS && c.inStaticContext()) {
            w.write(InnerClassRemover.OUTER_FIELD_NAME.toString());
            return;
        }
        if ((((X10Translator) tr).inInnerClass() || c.inAnonObjectScope()) && n.qualifier() == null
                && n.kind() != X10Special.SELF && n.kind() != Special.SUPER) {
            er.printType(n.type(), 0);
            w.write(".");
        } else if (n.qualifier() != null && n.kind() != X10Special.SELF && n.kind() != Special.SUPER) {
            er.printType(n.qualifier().type(), 0);
            w.write(".");
        }

        w.write(n.kind().toString());
    }

    @Override
    public void visit(ParExpr_c n) {
        n.translate(w, tr);
    }

    public void visit(SubtypeTest_c n) {
        TypeNode sub = n.subtype();
        TypeNode sup = n.supertype();

        w.write("((");
        new RuntimeTypeExpander(er, sub.type()).expand(tr);
        w.write(")");
        if (n.equals()) {
            w.write(".equals(");
        } else {
            w.write(".isAssignableTo(");
        }
        new RuntimeTypeExpander(er, sup.type()).expand(tr);
        w.write("))");
    }

    @Override
    public void visit(HasZeroTest_c n) {
        TypeNode sub = n.parameter();

        w.write("((");
        new RuntimeTypeExpander(er, sub.type()).expand(tr);
        w.write(").hasZero())");
    }

    @Override
    public void visit(Tuple_c c) {
        Type t = Types.getParameterType(c.type(), 0);

        w.write(X10_RUNTIME_IMPL_JAVA_ARRAYUTILS + ".<");
        er.printType(t, PRINT_TYPE_PARAMS | BOX_PRIMITIVES);
        w.write("> ");
        if (t.isParameterType()) {
            w.write("makeRailFromValues(");
            new RuntimeTypeExpander(er, t).expand();
            w.write(", ");
            new Join(er, ", ", c.arguments()).expand();
            w.write(")");
        } else {
            w.write("makeRailFromJavaArray(");
            new RuntimeTypeExpander(er, t).expand();
            w.write(", ");
            w.write("new ");
            er.printType(t, 0);
            w.write("[] {");
            new Join(er, ", ", c.arguments()).expand();
            w.write("}");
            w.write(")");
        }
    }

    @Override
    public void visit(Unary_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(X10Unary_c n) {
        Expr left = n.expr();
        Type l = left.type();
        Unary.Operator op = n.operator();

        if (op == Unary.POST_DEC || op == Unary.POST_INC || op == Unary.PRE_DEC || op == Unary.PRE_INC) {
            Expr expr = left;
            Type t = left.type();

            Expr target = null;
            List<Expr> args = null;
            List<TypeNode> typeArgs = null;
            MethodInstance mi = null;

            // Handle a(i)++ and a.apply(i)++
            if (expr instanceof ClosureCall) {
                ClosureCall e = (ClosureCall) expr;
                target = e.target();
                args = e.arguments();
                typeArgs = Collections.<TypeNode> emptyList(); // e.typeArgs();
                mi = e.closureInstance();
            } else if (expr instanceof X10Call) {
                X10Call e = (X10Call) expr;
                if (e.target() instanceof Expr && e.name().id() == ClosureCall.APPLY) {
                    target = (Expr) e.target();
                    args = e.arguments();
                    typeArgs = e.typeArguments();
                    mi = e.methodInstance();
                }
            }

            if (mi != null) {
                // MethodInstance setter = null;

                List<Type> setArgTypes = new ArrayList<Type>();
                List<Type> setTypeArgs = new ArrayList<Type>();
                for (Expr e : args) {
                    setArgTypes.add(e.type());
                }
                setArgTypes.add(expr.type());
                for (TypeNode tn : typeArgs) {
                    setTypeArgs.add(tn.type());
                }
                // try {
                // setter = ts.findMethod(target.type(), ts.MethodMatcher(t,
                // SettableAssign.SET, setTypeArgs, setArgTypes, tr.context()));
                // }
                // catch (SemanticException e) {
                // }

                // TODO: handle type args
                // TODO: handle setter method

                // TODO pretty print
                w.write("new " + JAVA_IO_SERIALIZABLE + "() {");
                w.allowBreak(0);
                w.write("final ");
                er.printType(t, PRINT_TYPE_PARAMS);
                w.write(" eval(");
                er.printType(target.type(), PRINT_TYPE_PARAMS);
                w.write(" target");
                {
                    int i = 0;
                    for (Expr e : args) {
                        w.write(", ");
                        er.printType(e.type(), PRINT_TYPE_PARAMS);
                        w.write(" a" + (i + 1));
                        i++;
                    }
                }
                w.write(") {");
                w.allowBreak(0);
                er.printType(left.type(), PRINT_TYPE_PARAMS);
                w.write(" old = ");
                String pat = Emitter.getJavaImplForDef(mi.x10Def());
                if (pat != null) {
                    Map<String,Object> components = new HashMap<String,Object>();
                    int j = 0;
                    Object component;
                    component = "target";
                    if (supportNumberedParameterForNative)
                    components.put(String.valueOf(j++), component);
                    components.put("target", component);
                    {
                        int i = 0;
                        for (Expr e : args) {
                            component = "a" + (i + 1);
                            if (supportNumberedParameterForNative)
                            components.put(String.valueOf(j++), component);
                            // TODO need check
                            components.put(mi.def().formalNames().get(i).name().toString(), component);
                            i++;
                        }
                    }
                    er.dumpRegex("Native", components, tr, pat);
                } else {
                    w.write("target.$apply(");
                    {
                        int i = 0;
                        for (Expr e : args) {
                            if (i > 0) w.write(", ");
                            w.write("a" + (i + 1));
                            i++;
                        }
                    }
                    w.write(")");
                }
                w.write(";");
                w.allowBreak(0);
                er.printType(left.type(), PRINT_TYPE_PARAMS);
                w.write(" neu = (");
                er.printType(left.type(), PRINT_TYPE_PARAMS);
                w.write(") old");
                w.write((op == Unary.POST_INC || op == Unary.PRE_INC ? "+" : "-") + "1");
                w.write(";");
                w.allowBreak(0);
                w.write("target.set(neu");
                {
                    int i = 0;
                    for (Expr e : args) {
                        w.write(", ");
                        w.write("a" + (i + 1));
                        i++;
                    }
                }
                w.write(");");
                w.allowBreak(0);
                w.write("return ");
                w.write((op == Unary.PRE_DEC || op == Unary.PRE_INC ? "neu" : "old"));
                w.write(";");
                w.allowBreak(0);
                w.write("}");
                w.allowBreak(0);
                w.write("}.eval(");
                tr.print(n, target, w);
                w.write(", ");
                new Join(er, ", ", args).expand(tr);
                w.write(")");

                return;
            }
        }

        if (l.isNumeric() || l.isBoolean()) {
            visit((Unary_c) n);
            return;
        }

        Name methodName = X10Unary_c.unaryMethodName(op);
        if (methodName != null)
            er.generateStaticOrInstanceCall(n.position(), left, methodName);
        else
            throw new InternalCompilerError("No method to implement " + n, n.position());
        return;
    }

    @Override
    public void visit(final Closure_c n) {
        // System.out.println(this + ": " + n.position() + ": " + n +
        // " captures "+n.closureDef().capturedEnvironment());
        Translator tr2 = ((X10Translator) tr).inInnerClass(true);
        tr2 = tr2.context(n.enterScope(tr2.context()));

        List<Expander> typeArgs = new ArrayList<Expander>();
        for (final Formal f : n.formals()) {
            TypeExpander ft = new TypeExpander(er, f.type().type(), PRINT_TYPE_PARAMS | BOX_PRIMITIVES);
            typeArgs.add(ft); // must box formals
        }

        boolean runAsync = false;
        MethodInstance_c mi = (MethodInstance_c) n.methodContainer();
        if (mi != null && mi.container().isClass()
                && mi.container().toClass().fullName().toString().equals("x10.xrx.Runtime")
                && mi.signature().startsWith("runAsync")) {
            runAsync = true;
        }

        TypeExpander ret = new TypeExpander(er, n.returnType().type(), PRINT_TYPE_PARAMS | BOX_PRIMITIVES);
        if (!n.returnType().type().isVoid()) {
            typeArgs.add(ret);
            w.write("new " + X10_FUN_CLASS_PREFIX + "_0_" + n.formals().size());
        } else {
            w.write("new " + X10_VOIDFUN_CLASS_PREFIX + "_0_" + n.formals().size());
        }

        if (typeArgs.size() > 0) {
            w.write("<");
            new Join(er, ", ", typeArgs).expand(tr2);
            w.write(">");
        }

        w.write("() {");

        List<Formal> formals = n.formals();
        // bridge
        boolean bridge = needBridge(n);
        if (bridge) {
            w.write("public final ");
            if (useSelfDispatch && n.returnType().type().isVoid() && n.formals().size() != 0) {
                w.write(JAVA_LANG_OBJECT);
            } else {
                ret.expand(tr2);
            }

            w.write(" ");

            er.printApplyMethodName(n, true);

            // print the formals
            w.write("(");
            for (int i = 0; i < formals.size(); ++i) {
                if (i != 0) w.write(",");
                er.printFormal(tr2, n, formals.get(i), true);

                if (useSelfDispatch) {
                    w.write(", ");
                    w.write(X10_RTT_TYPE);
                    w.write(" ");
                    w.write("t" + (i + 1));
                }
            }
            w.write(") { ");
            if (!n.returnType().type().isVoid()) {
                w.write("return ");
            }

            er.printApplyMethodName(n, n.returnType().type().isParameterType());

            w.write("(");
            String delim = "";
            for (Formal f : formals) {
                w.write(delim);
                delim = ",";
                if (isPrimitive(f.type().type())) {
                    // TODO:CAST
                    w.write("(");
                    er.printType(f.type().type(), 0);
                    w.write(")");
                }
                w.write(f.name().toString());
            }
            w.write(");");
            if (useSelfDispatch && n.returnType().type().isVoid() && n.formals().size() != 0) {
                w.write("return null;");
            }
            w.write("}");
            w.newline();
        }

        w.write("public final ");
        if (useSelfDispatch && !bridge && n.returnType().type().isVoid() && n.formals().size() != 0) {
            w.write(JAVA_LANG_OBJECT);
        } else {
            er.printType(n.returnType().type(), PRINT_TYPE_PARAMS);
        }

        w.write(" ");

        er.printApplyMethodName(n, n.returnType().type().isParameterType());

        w.write("(");
        for (int i = 0; i < formals.size(); i++) {
            if (i != 0) w.write(", ");
            er.printFormal(tr2, n, formals.get(i), false);
            if (useSelfDispatch && !bridge) {
                w.write(", ");
                w.write(X10_RTT_TYPE);
                w.write(" ");
                w.write("t" + (i + 1));
            }
        }

        w.write(")");

        // print the closure body
        w.write(" { ");

        List<Stmt> statements = n.body().statements();
        // boolean throwException = false;
        // boolean throwThrowable = false;
        // for (Stmt stmt : statements) {
        // final List<Type> throwables = getThrowables(stmt);
        // if (throwables == null) {
        // continue;
        // }
        // for (Type type : throwables) {
        // if (type != null) {
        // if (type.isSubtype(tr.typeSystem().Exception(), tr.context()) &&
        // !type.isSubtype(tr.typeSystem().RuntimeException(), tr.context())) {
        // throwException = true;
        // } else if (!type.isSubtype(tr.typeSystem().Exception(), tr.context())
        // && !type.isSubtype(tr.typeSystem().Error(), tr.context())) {
        // throwThrowable = true;
        // }
        // }
        // }
        // }

        // TODO remove wrapping with UnknownJavaThrowable
//        TryCatchExpander tryCatchExpander = new TryCatchExpander(w, er, n.body(), null);
//        if (runAsync) {
//            tryCatchExpander.addCatchBlock(X10_IMPL_UNKNOWN_JAVA_THROWABLE, "ex", new Expander(er) {
//                public void expand(Translator tr) {
//                    w.write("x10.xrx.Runtime.pushException(ex);");
//                }
//            });
//        }

        // if (throwThrowable) {
        // tryCatchExpander.addCatchBlock("java.lang.RuntimeException", "ex",
        // new Expander(er) {
        // public void expand(Translator tr) {
        // w.write("throw ex;");
        // }
        // });
        // tryCatchExpander.addCatchBlock("java.lang.Error", "er", new
        // Expander(er) {
        // public void expand(Translator tr) {
        // w.write("throw er;");
        // }
        // });
        // if (runAsync) {
        // tryCatchExpander.addCatchBlock("java.lang.Throwable", "t", new
        // Expander(er) {
        // public void expand(Translator tr) {
        // w.write("x10.xrx.Runtime.pushException(new " + X10_IMPL_UNKNOWN_JAVA_THROWABLE + "(t));");
        // }
        // });
        // } else {
        // tryCatchExpander.addCatchBlock("java.lang.Throwable", "t", new
        // Expander(er) {
        // public void expand(Translator tr) {
        // w.write("throw new " + X10_IMPL_UNKNOWN_JAVA_THROWABLE + "(t);");
        // }
        // });
        // }
        // tryCatchExpander.expand(tr2);
        // }
        // else
        // if (throwException) {
        // tryCatchExpander.addCatchBlock("java.lang.RuntimeException", "ex",
        // new Expander(er) {
        // public void expand(Translator tr) {
        // w.write("throw ex;");
        // }
        // });
        //
        // if (runAsync) {
        // tryCatchExpander.addCatchBlock("java.lang.Exception", "ex", new
        // Expander(er) {
        // public void expand(Translator tr) {
        // w.write("x10.xrx.Runtime.pushException(new " + X10_IMPL_UNKNOWN_JAVA_THROWABLE + "(ex));");
        // }
        // });
        // } else {
        // tryCatchExpander.addCatchBlock("java.lang.Exception", "ex", new
        // Expander(er) {
        // public void expand(Translator tr) {
        // w.write("throw new " + X10_IMPL_UNKNOWN_JAVA_THROWABLE + "(ex);");
        // }
        // });
        // }
        // tryCatchExpander.expand(tr2);
        // } else
        //

        // TODO remove wrapping with UnknownJavaThrowable
//        if (runAsync)
//            tryCatchExpander.expand(tr2);
//        else
            er.prettyPrint(n.body(), tr2);

        if (useSelfDispatch && !bridge && n.returnType().type().isVoid() && n.formals().size() != 0) {
            w.write("return null;");
        }

        w.write("}");
        w.newline();

        Type type = n.type();
        type = Types.baseType(type);
        if (type.isClass()) {
            X10ClassType ct = type.toClass();
            X10ClassDef def = ct.x10Def();

            // XTENLANG-1102
            Set<ClassDef> visited = CollectionFactory.newHashSet();

            visited = CollectionFactory.newHashSet();
            visited.add(def);
            if (!def.flags().isInterface()) {
                List<Type> types = new ArrayList<Type>();
                LinkedList<Type> worklist = new LinkedList<Type>();
                for (Type t : def.asType().interfaces()) {
                    Type it = Types.baseType(t);
                    worklist.add(it);
                }
                while (!worklist.isEmpty()) {
                    Type it = worklist.removeFirst();
                    if (it.isClass()) {
                        X10ClassType ct2 = it.toClass();
                        X10ClassDef idef = ct2.x10Def();

                        if (visited.contains(idef)) continue;
                        visited.add(idef);

                        for (Type t : ct2.interfaces()) {
                            Type it2 = Types.baseType(t);
                            worklist.add(it2);
                        }

                        if (ct2.typeArguments() != null) types.addAll(ct2.typeArguments());
                    }
                }
                // To extend Any, the type requires getRTT even if it has no type params (e.g. VoidFun_0_0).
                // if (types.size() > 0) {
                w.write("public x10.rtt.RuntimeType<?> " + GETRTT_NAME + "() { return " + RTT_NAME + "; }");
                w.newline();
                w.newline();

                w.write("public x10.rtt.Type<?> " + GETPARAM_NAME + "(int i) { ");
                for (int i = 0; i < types.size(); i++) {
                    w.write("if (i == " + i + ")");
                    Type t = types.get(i);
                    w.write(" return ");
                    new RuntimeTypeExpander(er, t).expand();
                    w.write("; ");
                }
                w.write("return null; ");
                w.write("}");
                w.newline();

                w.newline();

                // }
            }
        }

        w.write("}");
    }

    private boolean needBridge(final Closure_c n) {
        return containsPrimitive(n) || !n.returnType().type().isVoid() && !n.returnType().type().isParameterType();
    }

    // private boolean throwException(List<Stmt> statements) {
    // for (Stmt stmt : statements) {
    // final List<Type> exceptions = getThrowables(stmt);
    // if (exceptions == null) {
    // continue;
    // }
    // for (Type type : exceptions) {
    // if (type != null) {
    // if (type.isSubtype(tr.typeSystem().Exception(), tr.context()) &&
    // !type.isSubtype(tr.typeSystem().RuntimeException(), tr.context())) {
    // return true;
    // } else if (!type.isSubtype(tr.typeSystem().Exception(), tr.context()) &&
    // !type.isSubtype(tr.typeSystem().Error(), tr.context())) {
    // return true;
    // }
    // }
    // }
    // }
    // return false;
    // }
    // private static List<Type> getThrowables(Stmt stmt) {
    // final List<Type> throwables = new ArrayList<Type>();
    // stmt.visit(
    // new NodeVisitor() {
    // @Override
    // public Node leave(Node old, Node n, NodeVisitor v) {
    // /* if (n instanceof X10Call_c) {
    // List<Type> throwTypes = ((X10Call_c) n).methodInstance().throwTypes();
    // if (throwTypes != null) throwables.addAll(throwTypes);
    // }
    // if (n instanceof Throw) {
    // throwables.add(((Throw) n).expr().type());
    // }
    // if (n instanceof X10New_c) {
    // List<Type> throwTypes = ((X10New_c) n).procedureInstance().throwTypes();
    // if (throwTypes != null) throwables.addAll(throwTypes);
    // }
    // */
    // return n;
    // }
    // });
    // return throwables;
    // }

    private boolean containsPrimitive(Closure_c n) {
        Type t = n.returnType().type();
        if (isPrimitive(t)) {
            return true;
        }
        for (Formal f : n.formals()) {
            Type type = f.type().type();
            if (isPrimitive(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visit(ClosureCall_c c) {
        Expr target = c.target();
        Type targetType = target.type();
        boolean base = false;

        TypeSystem xts = targetType.typeSystem();

        MethodInstance mi = c.closureInstance();

        Expr expr = target;
        if (target instanceof ParExpr) {
            expr = ((ParExpr) target).expr();
        }
        boolean newClosure = expr instanceof Closure_c;

        if (!newClosure) {
            // TODO:CAST
            w.write("(");
            w.write("(");
            er.printType(mi.container(), PRINT_TYPE_PARAMS);
            w.write(")");

            if (xts.isParameterType(targetType)) {
                w.write(X10_RTT_TYPES);
                w.write(".conversion(");
                new RuntimeTypeExpander(er, Types.baseType(mi.container())).expand(tr);
                w.write(",");
                c.printSubExpr(target, w, tr);
                w.write(")");
            } else {
                c.printSubExpr(target, w, tr);
            }
            w.write(")");
        } else {
            c.printSubExpr(target, w, tr);
        }

        w.write(".");

        er.printApplyMethodName(mi, newClosure);

        // print the argument list
        w.write("(");
        w.begin(0);

        for (Iterator<Type> i = mi.typeParameters().iterator(); i.hasNext();) {
            final Type at = i.next();
            new RuntimeTypeExpander(er, at).expand(tr);
            if (i.hasNext() || c.arguments().size() > 0) {
                w.write(", ");
            }
        }

        List<Expr> l = c.arguments();
        for (int i = 0; i < l.size(); i++) {
            Expr e = l.get(i);

            Type castType = mi.formalTypes().get(i);
            Type defType = mi.def().formalTypes().get(i).get();

            boolean closeParen = false;
            if (isString(e.type()) && !isString(castType)) {

                w.write("(");
                er.printType(castType, 0);
                w.write(")");

                if (xts.isParameterType(castType)) {
                    w.write(X10_RTT_TYPES);
                    w.write(".conversion(");
                    new RuntimeTypeExpander(er, Types.baseType(castType)).expand(tr);
                    w.write(",");
                    closeParen = true;
                }
            } if (!isBoxedType(e.type()) /*&& isBoxedType(defType)*/) {
                // primitives need to be boxed 
                er.printBoxConversion(e.type());
                w.write("(");
                closeParen = true;
            }

            c.print(e, w, tr);

            if (isMutableStruct(e.type())) {
                w.write(".clone()");
            }

            if (closeParen) {
                w.write(")");
            }

            if (useSelfDispatch && (!newClosure || !needBridge((Closure_c) expr))) {
                w.write(", ");
                new RuntimeTypeExpander(er, mi.formalTypes().get(i)).expand();
            }
            if (i != l.size() - 1) {
                w.write(", ");
            }
        }
        w.end();
        w.write(")");
    }

    @Override
    public void visit(StmtExpr_c n) {
        final Context c = tr.context();
        final ArrayList<LocalInstance> capturedVars = new ArrayList<LocalInstance>();
        // This visitor (a) finds all captured local variables,
        // (b) adds a qualifier to every "this", and
        // (c) rewrites fields and calls to use an explicit "this" target.
        n = (StmtExpr_c) n.visit(new ContextVisitor(tr.job(), tr.typeSystem(), tr.nodeFactory()) {
            public Node leaveCall(Node n) {
                if (n instanceof Local) {
                    Local l = (Local) n;
                    assert (l.name() != null) : l.position().toString();
                    VarInstance<?> found = c.findVariableSilent(l.name().id());
                    if (found != null) {
                        VarInstance<?> local = context().findVariableSilent(l.name().id());
                        if (found.def() == local.def()) {
                            assert (found.def() == l.localInstance().def()) : l.toString();
                            capturedVars.add(l.localInstance());
                        }
                    }
                }
                if (n instanceof Field_c) {
                    Field_c f = (Field_c) n;
                    return f.target(f.target()).targetImplicit(false);
                }
                if (n instanceof X10Call_c) {
                    X10Call_c l = (X10Call_c) n;
                    return l.target(l.target()).targetImplicit(false);
                }
                if (n instanceof Special_c) {
                    NodeFactory nf = nodeFactory();
                    Special_c s = (Special_c) n;
                    if (s.qualifier() == null) {
                        return s.qualifier(nf.CanonicalTypeNode(n.position(), s.type()));
                    }
                }
                return n;
            }
        }.context(c.pushBlock()));

        /*
         * N.B. this is workaround for front-end bug that generates non-final variable access
         * in the body of statement expression when constructor splitting is enabled.
         */
        if (supportConstructorSplitting && config.OPTIMIZE && config.SPLIT_CONSTRUCTORS) {
            w.write("(new " + JAVA_IO_SERIALIZABLE + "() { ");
            er.printType(n.type(), PRINT_TYPE_PARAMS);
            w.write(" eval(");
            String delim = null;
            for (LocalInstance li : capturedVars) {
                if (!li.flags().isFinal()) {
                    if (delim == null) {
                        delim = ",";
                    } else {
                        w.write(",");
                    }
                    w.write("final ");
                    er.printType(li.type(), PRINT_TYPE_PARAMS);
                    w.write(" ");
                    w.write(Emitter.mangleToJava(li.name()));
                    // System.err.println("Bad statement expression: " + n +
                    // " at "
                    // + n.position()); // DEBUG
                    // n.dump(System.err); // DEBUG
                    // throw new
                    // InternalCompilerError("Statement expression uses non-final variable "
                    // + li + "(at "
                    // + li.position() + ") from the outer scope",
                    // n.position());
                }
            }
            w.write(") {");
            w.newline(4);
            w.begin(0);
            Translator tr = this.tr.context(c.pushBlock());
            List<Stmt> statements = n.statements();
            for (Stmt stmt : statements) {
                tr.print(n, stmt, w);
                w.newline();
            }
            w.write("return ");
            tr.print(n, n.result(), w);
            w.write(";");
            w.end();
            w.newline();
            w.write("} }.eval(");

            delim = null;
            for (LocalInstance li : capturedVars) {
                if (!li.flags().isFinal()) {
                    if (delim == null) {
                        delim = ",";
                    } else {
                        w.write(",");
                    }
                    w.write(Emitter.mangleToJava(li.name()));
                }
            }
            w.write("))");
            return;
        }
        for (LocalInstance li : capturedVars) {
            if (!li.flags().isFinal()) {
                System.err.println("Bad statement expression: " + n + " at " + n.position()); // DEBUG
                n.dump(System.err); // DEBUG
                throw new InternalCompilerError("Statement expression uses non-final variable " + li + "(at "
                        + li.position() + ") from the outer scope", n.position());
            }
        }
        w.write("(new " + JAVA_IO_SERIALIZABLE + "() { ");
        er.printType(n.type(), PRINT_TYPE_PARAMS);
        w.write(" eval() {");
        w.newline(4);
        w.begin(0);
        Translator tr = this.tr.context(c.pushBlock());
        List<Stmt> statements = n.statements();
        for (Stmt stmt : statements) {
            tr.print(n, stmt, w);
            w.newline();
        }
        w.write("return ");
        tr.print(n, n.result(), w);
        w.write(";");
        w.end();
        w.newline();
        w.write("} }.eval())");
    }

    // ////////////////////////////////
    // end of Expr
    // ////////////////////////////////

    @Override
    public void visit(FieldDecl_c n) {
    	
    	/* Nobita code */
    	if (iterationCount == 0) {
    		VarWithLineNo temp1 = currClass.peek();
    		//System.out.println("THE REAL TYPE VALUE STRING: " + n.type().toString());
    		ClassInfo fieldDetails = new ClassInfo("field", n.type().nameString(), n.name().toString());
    		fieldDetails.x10Type = n.type().toString();
    		LinkedList<ClassInfo> fi = classDetails.get(temp1.name);
    		fi.add(fieldDetails);
    		
    		String varType = n.type().nameString();
    		if(varType != null && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
            		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
            		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
            		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
    			String packFullName = n.type().type().fullName().toString();
    			int packLen = packFullName.length();
    			boolean present = false;
    			String packName = "";
    			for (int i=0; i < packLen-1; i++) {
    				if (packFullName.charAt(i) == '.') {
    					present = true;
    					break;
    				}
    				else {
    					packName = packName + packFullName.charAt(i);
    				}
    			}
    			
    			if(present) {
    				//System.out.println("Arun yes I am here: " + packName);
    				if (!packageName.containsKey(n.type().nameString().toString())) {
    					packageName.put(n.type().nameString().toString(), packName);
    				}
    			}
    		}
    	}
    	/* Nobita code */
    	
    	
    	
        Flags flags;
        if (!n.flags().flags().isStatic()) {
            flags = n.flags().flags().clearFinal();
        } else {
            flags = n.flags().flags();
        }
        flags = flags.retainJava(); // ensure that X10Flags are not printed out
                                    // .. javac will not know what to do with
                                    // them.

        FieldDecl_c javaNode = (FieldDecl_c) n.flags(n.flags().flags(flags));
        

        // same with FiledDecl_c#prettyPrint(CodeWriter w, PrettyPrinter tr)
        FieldDef fieldDef = javaNode.fieldDef();
        boolean isInterface = fieldDef != null && fieldDef.container() != null
                && fieldDef.container().get().toClass().flags().isInterface();

        Flags f = javaNode.flags().flags();

        if (isInterface) {
            f = f.clearPublic();
            f = f.clearStatic();
            f = f.clearFinal();
        }

        // print volatile modifier
        boolean isVolatile = false;
        try {
            if (!((X10FieldDef_c)fieldDef).annotationsMatching(getType("x10.compiler.Volatile")).isEmpty()) {
                isVolatile = true;
            }
        } catch (SemanticException e) {
        }
        if (isVolatile) {
            w.write("volatile ");
        }

        w.write(f.translateJava());
        er.printType(javaNode.type().type(), PRINT_TYPE_PARAMS);
        // tr.print(javaNode, javaNode.type(), w);
        w.write(" ");
        tr.print(javaNode, javaNode.name(), w);

        if (javaNode.init() != null) {
            w.write(" = ");

            // X10 unique
            er.coerce(javaNode, javaNode.init(), javaNode.type().type());
        }

        w.write(";");
    }

    @Override
    public void visit(Formal_c f) {
        if (f.name().id().toString().equals("")) f = (Formal_c) f.name(f.name().id(Name.makeFresh("a")));
        f.translate(w, tr);
    }

    @Override
    public void visit(X10MethodDecl_c n) {
        // should be able to assert n.name() is not typeName here, once we stop generating such decls somewhere in the frontend...
    	
    	
		/* Nobita code - for the normal methods*/
		//String test = dec.name().toString();
		//System.out.println("The method name: " + n.name().toString());
		
		lineNo++;
		if ((currClass.size() > 0) && !(n.name().toString().equalsIgnoreCase("operator()"))) {
			
			//getting the current class details
			VarWithLineNo temp = currClass.peek();
			
			//getting the current method details
			VarWithLineNo temp1 = new VarWithLineNo(n.name().toString(), lineNo);
			currMethod.push(temp1);
			
			if (iterationCount == 0) {
				ClassInfo fieldDetails = new ClassInfo("method", n.returnType().toString(), n.name().toString());
				fieldDetails.classNo = temp.lineNo;
				fieldDetails.methodNo = lineNo;
				LinkedList<ClassInfo> fi = classDetails.get(temp.name);
				fi.add(fieldDetails);
			}
        	
        	
        	//the sets 
        	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp.lineNo);
        	
        	if (setMethodInfo != null) {
	        	//this is for inserting the method line no
	        	if (!setMethodInfo.containsKey(lineNo)) {
	        		setMethodInfo.put(lineNo, new HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>());
	        		//update modifier boolean
	        	}
	        	
	        	//this is for inserting the place line no
	        	HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(lineNo);
	        	
	        	//if(!placeInfo.containsKey(lineNo)) {
	        		placeInfo.put(lineNo, new HashMap<String, HashMap<String, HashSet<String>>>());
	        		//update modifier boolean
	        	//}
	        	
	        	//this is for inserting the sets
	        	HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(lineNo);
	        	
	        	//if (!setDetails.containsKey("RS")) {
	        		setDetails.put("RS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("CRS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("WS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("MWS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("CWS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("OS", new HashMap<String, HashSet<String>>());
	            	setDetails.put("OVS", new HashMap<String, HashSet<String>>());
	            	//update modifier boolean
	        	//}
	        	
        	}
        	
        	//the object
        	HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo2 = objInfo.get(temp.lineNo);
        	//if (!methodInfo2.containsKey(lineNo)) {
        		methodInfo2.put(lineNo, new HashMap<Integer, HashMap<String, ObjNode>>());
        		
        		//the place insertion && lineNo is given as default place
        		HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo2.get(lineNo);
        		placeInfo.put(lineNo, new HashMap<String, ObjNode>());
        		
        		//inserting the null object
        		ObjNode nullObj = new ObjNode("Obj-null", "null");
        		
        		//inserting the this object
        		ObjNode thisObj = new ObjNode("obj-this", temp.name);
        		HashMap<String, ObjNode> objDetails = placeInfo.get(lineNo);
        		objDetails.put("Obj-null", nullObj);
        		objDetails.put("obj-this", thisObj);
        		
        		
        		//the set
            	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo1 = setInfo.get(temp.lineNo);
            	if (setMethodInfo1 != null) {
            		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfoSet = setMethodInfo.get(lineNo);
            		if (placeInfoSet != null) {
            			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfoSet.get(lineNo);
            			if (setDetails != null) {
            				HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
        					if (!readSet.containsKey("obj-this")) {
        						readSet.put("obj-this", new HashSet<String>());
        						//update modifier boolean
        					}
                    		HashMap<String, HashSet<String>> cumReadSet = setDetails.get("CRS");
                    		if (!cumReadSet.containsKey("obj-this")) {
                    			cumReadSet.put("obj-this", new HashSet<String>());
                    			//update modifier boolean
                    		}
                    		HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
                    		if (!writeSet.containsKey("obj-this")) {
                    			writeSet.put("obj-this", new HashSet<String>());
                    			//update modifier boolean
                    		}
                    		HashMap<String, HashSet<String>> mWriteSet = setDetails.get("MWS");
                    		if (!mWriteSet.containsKey("obj-this")) {
                    			mWriteSet.put("obj-this", new HashSet<String>());
                    			//update modifier boolean
                    		}
                    		HashMap<String, HashSet<String>> cumWriteSet = setDetails.get("CWS");
                    		if (!cumWriteSet.containsKey("obj-this")) {
                    			cumWriteSet.put("obj-this", new HashSet<String>());
                    			//update modifier boolean
                    		}
                    		HashMap<String, HashSet<String>> objectSet = setDetails.get("OS");
                    		if (!objectSet.containsKey("obj-this")) {
                    			objectSet.put("obj-this", new HashSet<String>()); 
                    			//update modifier boolean
                    		}
                    		HashMap<String, HashSet<String>> objectVarSet = setDetails.get("OVS");
                    		if (!objectVarSet.containsKey("global-ovs")) {
                    			objectVarSet.put("global-ovs", new HashSet<String>());
                    			//update modifier boolean
                    		}
            			}
            		}
            	}
        		
        		//update modifier boolean
        	//}
        	
        	
        	//the graph
        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo1 = graphInfo.get(temp.lineNo);
        	//for the recursive function assistance
        	if (methodInfo1 != null) {
        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo1.get(lineNo);
        		if (lineInfo != null) {
        			recVarInfo = lineInfo.get(lineNo);
        		}
        	}
        	
        	
        	if (/*!methodInfo1.containsKey(lineNo)*/ true) {
    			methodInfo1.put(lineNo, new HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>());
    			
    			HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo1.get(lineNo);
    			lineInfo.put(lineNo, new HashMap<String, LinkedList<EdgeRep>>());
    			
    			HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
    			varInfo.put("this", new LinkedList<EdgeRep>());
    			
    			LinkedList<EdgeRep> edgeIncl = varInfo.get("this");
    			EdgeRep edgeInfo = new EdgeRep("P","obj-this");
    			edgeIncl.addLast(edgeInfo);
    			
        		//this is for the object fields of the obj-this
            	LinkedList<ClassInfo> ll = classDetails.get(temp.name);
            	if (ll != null) {
            		Iterator it = ll.iterator();
            		
            		while (it.hasNext()) {
            			ClassInfo fd = (ClassInfo)it.next();
            			if (fd.classifier.equalsIgnoreCase("field")) {
                    		if((fd != null) && !((fd.type.equalsIgnoreCase("Long")) || (fd.type.equalsIgnoreCase("Float")) || (fd.type.equalsIgnoreCase("String")) || (fd.type.equalsIgnoreCase("FileReader")) || (fd.type.equalsIgnoreCase("Printer")) || (fd.type.equalsIgnoreCase("Random")) || (fd.type.equalsIgnoreCase("FileWriter")) || 
                            		(fd.type.equalsIgnoreCase("Double")) || (fd.type.equalsIgnoreCase("Char")) || (fd.type.equalsIgnoreCase("PlaceGroup")) || (fd.type.equalsIgnoreCase("File")) || (fd.type.equalsIgnoreCase("FailedDynamicCheckException")) || (fd.type.equalsIgnoreCase("FinishState")) || (fd.type.equalsIgnoreCase("LongRange")) ||
                            		(fd.type.equalsIgnoreCase("Boolean")) || (fd.type.equalsIgnoreCase("Rail")) || (fd.type.equalsIgnoreCase("Place")) || (fd.type.equalsIgnoreCase("Dist")) || (fd.type.equalsIgnoreCase("ArrayList")) || (fd.type.equalsIgnoreCase("Iterator")) || (fd.type.equalsIgnoreCase("Point")) || (fd.type.equalsIgnoreCase("Int")) ||
                            		(fd.type.equalsIgnoreCase("Array")) || (fd.type.equalsIgnoreCase("DistArray")) || (fd.type.equalsIgnoreCase("Region")) || (fd.type.equalsIgnoreCase("GlobalRef")))) {
                    			
                    			if(!(varInfo.containsKey("obj-this"))) {
                            		varInfo.put("obj-this", new LinkedList<EdgeRep>());
                            		//update modifier boolean
                            	}
                    			
                    			LinkedList<EdgeRep> edgeInclField = varInfo.get("obj-this");
                    			if (edgeInclField != null) {
                    				EdgeRep edgeInfo1 = new EdgeRep("F","Obj-null",fd.name);
                    				edgeInclField.addLast(edgeInfo1);
                    			}
                    		}
            			}
            		}
            	}
    			
    		}
        	
        	HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo1.get(lineNo);
        	HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
        	lastGraphInfo.push(deepCopy(varInfo));
        	
        	//inserting into currPlace
        	VarWithLineNo temp2 = new VarWithLineNo(("place" + lineNo), lineNo);
        	currPlace.push(temp2);
        	
        	//the place tree
        	HashMap<Integer, PlaceNode> methodInfoPlace = placeTree.get(temp.lineNo);
        	if (!methodInfoPlace.containsKey(lineNo)) {
        		PlaceNode pn = new PlaceNode(n.name().toString());
        		methodInfoPlace.put(lineNo, pn);
        		//update modifier boolean
        	}
        	
        	//for the inter-procedural analysis
        	//call the function here
        			
		}
		/* Nobita code */
    	
		
		/* Nobita code - for the closure methods*/
		if ((currClass.size() > 0) && (n.name().toString().equalsIgnoreCase("operator()")) && (closureInfo.size() > 0)) {
			//getting the current class details
			VarWithLineNo temp = currClass.peek();
			
			ClosureDetails cd = closureInfo.get(temp.name);
			
			//pushing the method
			VarWithLineNo temp1 = new VarWithLineNo(n.name().toString(), lineNo);
			currMethod.push(temp1);
			
			ClassInfo fieldDetails = new ClassInfo("method", n.returnType().toString(), n.name().toString());
        	LinkedList<ClassInfo> fi = classDetails.get(temp.name);
        	fi.add(fieldDetails);
        	
        	//the sets 
        	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp.lineNo);
        	
        	if (setMethodInfo != null) {

	        	//this is for inserting the method line no
	        	//if (!setMethodInfo.containsKey(lineNo)) {
	        		setMethodInfo.put(lineNo, new HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>());
	        		//update modifier boolean
	        	//}
	        	
	        	//this is for inserting the place line no
	        	HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(lineNo);
	        	
	        	if(!placeInfo.containsKey(lineNo)) {
	        		placeInfo.put(lineNo, new HashMap<String, HashMap<String, HashSet<String>>>());
	        		//update modifier boolean
	        	}
	        	
	        	if (cd != null) {
		        	//this is for inserting the sets - the union of the set of write
		        	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo3 = setInfo.get(cd.fromClass);
		        	if (setMethodInfo3 != null) {
		        		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo3 = setMethodInfo3.get(cd.fromMethod);
		        		if (placeInfo3 != null) {
		        			HashMap<String, HashMap<String, HashSet<String>>> setDetails3 = placeInfo3.get(cd.fromplace);
		        			if (setDetails3 != null) {
		        				HashMap<String, HashMap<String, HashSet<String>>> setDetails = copySetToNewPlace(setDetails3);
		        				placeInfo.put(lineNo, setDetails);
		        			}
		        		}
		        	}
	        	}
        	}
        	
        	//the object
        	HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo2 = objInfo.get(temp.lineNo);
        	//if (!methodInfo2.containsKey(lineNo)) {
        		methodInfo2.put(lineNo, new HashMap<Integer, HashMap<String, ObjNode>>());
        		
        		//the place insertion && lineNo is given as default place
        		HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo2.get(lineNo);
        		placeInfo.put(lineNo, new HashMap<String, ObjNode>());
        		
        		if (cd != null) {
	        		HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo3 = objInfo.get(cd.fromClass);
	        		if (methodInfo3 != null) {
	        			HashMap<Integer, HashMap<String, ObjNode>> placeInfo3 = methodInfo3.get(cd.fromMethod);
	        			if (placeInfo3 != null) {
	        				HashMap<String, ObjNode> objDetail3 = placeInfo3.get(cd.fromplace);
	        				HashMap<String, ObjNode> theObject = copyObjectToNewPlace(objDetail3);
	        				
	        				placeInfo.put(lineNo, theObject);
	        			}
	        		}
        		}
        	//}
        	
        	
        	//the graph
        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo1 = graphInfo.get(temp.lineNo);
        	//if (!methodInfo1.containsKey(lineNo)) {
        		methodInfo1.put(lineNo, new HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>());
        		
        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo1.get(lineNo);
        		lineInfo.put(lineNo, new HashMap<String, LinkedList<EdgeRep>>());
        		
        		if (cd != null) {
	        		HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo3 = graphInfo.get(cd.fromClass);
	        		if (methodInfo3 != null) {
	        			HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo3 = methodInfo3.get(cd.fromMethod);
	        			if (lineInfo3 != null) {
	        				HashMap<String, LinkedList<EdgeRep>> varInfo3 = lineInfo3.get(cd.fromplace);
	        				HashMap<String, LinkedList<EdgeRep>> varDetails = copyGraphToNewPlace(varInfo3);
	        				
	        				//not required for the closure
	        				LinkedList<EdgeRep> outVar = varDetails.get("this");
	        				if (outVar != null) {
	        					varDetails.put("out$$", outVar);
	        				}
	        				varDetails.remove("this");
	        				
	        				lineInfo.put(lineNo, varDetails);
	        			}
	        		}
        		}
        	//}
        	
        	HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo2 = methodInfo1.get(lineNo);
        	HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo2.get(lineNo);
        	lastGraphInfo.push(deepCopy(varInfo));
        	
        	//inserting into currPlace
        	VarWithLineNo temp2 = new VarWithLineNo(("place" + lineNo), lineNo);
        	currPlace.push(temp2);
        	
        	//the place tree
        	HashMap<Integer, PlaceNode> methodInfoPlace = placeTree.get(temp.lineNo);
        	if (!methodInfoPlace.containsKey(lineNo)) {
        		PlaceNode pn = new PlaceNode(n.name().toString());
        		methodInfoPlace.put(lineNo, pn);
        		//update modifier boolean
        	}
        		
			
		}
		/* Nobita code */
    	
        if (er.printMainMethod(n)) {
        	
        	int lineNo1 = lineNo;
        	lineNo++;
        	/* the printer code */
        	/*
        	VarWithLineNo temp1 = currClass.peek();
        	VarWithLineNo temp2 = currMethod.peek();
        	VarWithLineNo temp3 = currPlace.peek();
        	//System.out.println("I AM PRINTING: " + temp1.lineNo +":"+ temp2.lineNo +":"+ temp3.lineNo +":" + lineNo);
        	//HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
        	//if (methodInfo != null) {
        		//HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
        		//if (lineInfo != null) { 
        			HashMap<String, LinkedList<EdgeRep>> varInfo = lastGraphInfo.peek();
        			if (varInfo != null) {
        				System.out.println("For the Method: " + temp2.name);
        				Iterator it = varInfo.entrySet().iterator();
        	        	while (it.hasNext()) {
        	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
        	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
        	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
        	        		Iterator it1 = edgeIncl.iterator();
        	        		while (it1.hasNext())
        	        		{
        	        			EdgeRep er = (EdgeRep)it1.next();
        	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
        	        					+ er.fieldName + " EDGE-Name:" + er.edgeName);
        	        		}
        	        	}
        			}
        		//}
        	//}
        	
        	
        	//the sets
        	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	if (setMethodInfo != null) {
        		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        		if (placeInfo != null) {
        			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
        			if (setDetails != null) {
        				System.out.println();
    		    		System.out.println("Method's Object Sets:");
    		    		
    		    		System.out.println("Read Set:");
    		    		HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
    		    		if (readSet != null) {
    		    			Iterator it3 = readSet.entrySet().iterator();
    		    			while (it3.hasNext()) {
    		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it3.next();
    		        			System.out.println("Object: " + pair.getKey());
    		        			HashSet<String> rs = (HashSet<String>)pair.getValue();
    		        			
    		        			if(rs != null) {
    		        				Iterator it4 = rs.iterator();
    		        				
    		        				while (it4.hasNext()) {
    		            				String str = (String)it4.next();
    		            				System.out.print(" {" + str +"} ");
    		            			}
    		            			System.out.println("");
    		        			}
    		    			}
    		    		}
    		    		
    		    		System.out.println("");
    		    		
    		    		
    		    		System.out.println("Cumulative Read Set:");
    		    		HashMap<String, HashSet<String>> creadSet = setDetails.get("CRS");
    		    		if (creadSet != null) {
    		    			Iterator it3 = creadSet.entrySet().iterator();
    		    			while (it3.hasNext()) {
    		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it3.next();
    		        			System.out.println("Object: " + pair.getKey());
    		        			HashSet<String> rs = (HashSet<String>)pair.getValue();
    		        			
    		        			if(rs != null) {
    		        				Iterator it4 = rs.iterator();
    		        				
    		        				while (it4.hasNext()) {
    		            				String str = (String)it4.next();
    		            				System.out.print(" {" + str +"} ");
    		            			}
    		            			System.out.println("");
    		        			}
    		    			}
    		    		}
    		    		
    		    		System.out.println("");
    		    		
    		    		
    		    		System.out.println("Must Write Set: ");
    		    		HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
    		    		if (writeSet != null) {
    		    			Iterator it1 = writeSet.entrySet().iterator();
    		    			
    		    			while (it1.hasNext()) {
    		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it1.next();
    		        			System.out.println("Object: " + pair.getKey());
    		        			HashSet<String> ws = (HashSet<String>)pair.getValue();
    		        			
    		        			if(ws != null) {
    		        				Iterator it2 = ws.iterator();
    		            			while (it2.hasNext()) {
    		            				String str = (String)it2.next();
    		            				System.out.print(" {" + str +"} ");
    		            			}
    		            			System.out.println("");
    		        			}
    		    			}
    		    		}
    		    		
    		    		System.out.println("");
    		    		System.out.println("May Write Set: ");
    		    		HashMap<String, HashSet<String>> mWriteSet = setDetails.get("MWS");
    		    		if (mWriteSet != null) {
    		    			Iterator it5 = mWriteSet.entrySet().iterator();
    		    			while (it5.hasNext()) {
    		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it5.next();
    		        			System.out.println("Object: " + pair.getKey());
    		        			HashSet<String> mws = (HashSet<String>)pair.getValue();
    		        			
    		        			if(mws != null) {
    		            			Iterator it6 = mws.iterator();
    		            			while (it6.hasNext()) {
    		            				String str = (String)it6.next();
    		            				System.out.print(" {" + str +"} ");
    		            			}
    		            			System.out.println("");
    		        			}
    		    			}
    		    		}
    		    		
    		    		
    		    		System.out.println("");
    		    		
    		    		System.out.println("OV Set: ");
    		    		HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
    		    		if (ovSet != null) {
    		    			Iterator it1 = ovSet.entrySet().iterator();
    		    			
    		    			while (it1.hasNext()) {
    		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it1.next();
    		        			System.out.println("Object: " + pair.getKey());
    		        			HashSet<String> ovs = (HashSet<String>)pair.getValue();
    		        			
    		        			if(ovs != null) {
    		        				Iterator it2 = ovs.iterator();
    		            			while (it2.hasNext()) {
    		            				String str = (String)it2.next();
    		            				System.out.print(" {" + str +"} ");
    		            			}
    		            			System.out.println("");
    		        			}
    		    			}
    		    		}
        			}
        		}
        	}
        	System.out.println("-----------------------------------end of main method---------------------------------");
        	*/
        	/* the printer code */
        	
        	
        	
        	/* Nobita code */
    		currMethod.pop();
    		currPlace.pop();
    		lastGraphInfo.removeAllElements();
    		recVarInfo = null;
    		/* Nobita code */
            return;
        }
        er.generateMethodDecl(n, false);
        
        /* Nobita code - this is for hte closure storing back the set values */
        lineNo++;
        //System.out.println("the arrogant :" + currClass.peek().name);
        if (n.name().toString().equalsIgnoreCase("operator()") && closureInfo.containsKey(currClass.peek().name)) {
        	//getting the current class details
			VarWithLineNo temp1 = currClass.peek();
			VarWithLineNo temp2 = currMethod.peek();
			VarWithLineNo temp3 = currPlace.peek();
			
			ClosureDetails cdMethod = closureInfo.get(temp1.name);
			if (cdMethod != null) {
			cdMethod.toClass = temp1.lineNo;
			cdMethod.toMethod = temp2.lineNo;
			cdMethod.toplace = temp3.lineNo;
			}
			
			
			HashMap<Integer, PlaceNode> methodInfoPlace = placeTree.get(temp1.lineNo);
			if (methodInfoPlace != null) {
				PlaceNode pn = methodInfoPlace.get(temp2.lineNo);
				
				//for the child
				if (pn.child != null) {
					PlaceNode pn1 = pn.child;
					
					ClosureDetails cd = closureInfo.get(pn1.toName);
					
					HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(cd.toClass);
					if (setMethodInfo != null) {
						HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(cd.toMethod);
						if (placeInfo != null) {
							HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(cd.toplace);
							if (setDetails != null) {
								HashMap<String, HashSet<String>> rs = setDetails.get("RS");
								HashMap<String, HashSet<String>> crs = setDetails.get("CRS");
								HashMap<String, HashSet<String>> ws = setDetails.get("WS");
								HashMap<String, HashSet<String>> ovs = setDetails.get("OVS");
								
								if (rs != null && crs != null && ws != null && ovs != null) {
									
									//for the ovs only
									HashSet<String> set11 = ovs.get("global-ovs");
									
									Iterator it = rs.entrySet().iterator();
									
									while (it.hasNext()) {
										Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it.next();
										
										HashSet<String> set1 = crs.get(phase3.getKey());
										HashSet<String> set2 = ws.get(phase3.getKey());
										HashSet<String> set3 = rs.get(phase3.getKey());
										
										if (set1 != null && set2 != null && set3 != null) {
										
											set1.removeAll(set2);
											
											set1.addAll(set3);
											
											HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo1 = setInfo.get(temp1.lineNo);
											if (setMethodInfo1 != null) {
												HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo1 = setMethodInfo1.get(temp2.lineNo);
												if (placeInfo1 != null) {
													HashMap<String, HashMap<String, HashSet<String>>> setDetails1 = placeInfo1.get(temp3.lineNo);
													if (setDetails1 != null) {
														HashMap<String, HashSet<String>> crsMethod = setDetails1.get("CRS");
														HashMap<String, HashSet<String>> ovsMethod = setDetails1.get("OVS");
														
														HashSet<String> set4 = crsMethod.get(phase3.getKey());
														if (set4 != null) {
															set4.addAll(set1);
														}
														
														HashSet<String> set12 = ovsMethod.get("global-ovs");
														if (set12 != null) {
															set12.addAll(set11);
														}
														
													}
												}
											}
										}
									}
								}
								
							}
						}
					}
					
					
					//for the siblings
					while (pn1.sibling != null) {
						pn1 = pn1.sibling;
						
						cd = closureInfo.get(pn1.toName);
						
						setMethodInfo = setInfo.get(cd.toClass);
						if (setMethodInfo != null) {
							HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(cd.toMethod);
							if (placeInfo != null) {
								HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(cd.toplace);
								if (setDetails != null) {
									HashMap<String, HashSet<String>> rs = setDetails.get("RS");
									HashMap<String, HashSet<String>> crs = setDetails.get("CRS");
									HashMap<String, HashSet<String>> ws = setDetails.get("WS");
									HashMap<String, HashSet<String>> ovs = setDetails.get("OVS");
									
									if (rs != null && crs != null && ws != null) {
										
										//for the ovs only
										HashSet<String> set11 = ovs.get("global-ovs");
										
										Iterator it = rs.entrySet().iterator();
										
										while (it.hasNext()) {
											Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it.next();
											
											HashSet<String> set1 = crs.get(phase3.getKey());
											HashSet<String> set2 = ws.get(phase3.getKey());
											
											HashSet<String> set3 = rs.get(phase3.getKey());
											
											if (set1 != null && set2 != null && set3 != null) {
												set1.removeAll(set2);
												
												set1.addAll(set3);
												
												HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo1 = setInfo.get(temp1.lineNo);
												if (setMethodInfo1 != null) {
													HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo1 = setMethodInfo1.get(temp2.lineNo);
													if (placeInfo1 != null) {
														HashMap<String, HashMap<String, HashSet<String>>> setDetails1 = placeInfo1.get(temp3.lineNo);
														if (setDetails1 != null) {
															HashMap<String, HashSet<String>> crsMethod = setDetails1.get("CRS");
															HashMap<String, HashSet<String>> ovsMethod = setDetails1.get("OVS");
															
															HashSet<String> set4 = crsMethod.get(phase3.getKey());
															if (set4 != null) {
																set4.addAll(set1);
															}
															
															HashSet<String> set12 = ovsMethod.get("global-ovs");
															if (set12 != null) {
																set12.addAll(set11);
															}
														}
													}
												}
											}
										}
									}
									
								}
							}
						}
					}
					
				}
			}
        }
        
        
        //union of read and cumulative read sets for other than main and operator()
        if (!n.name().toString().equalsIgnoreCase("main") && !n.name().toString().equalsIgnoreCase("operator()")) {
        	//getting the current class details
			VarWithLineNo temp1 = currClass.peek();
			VarWithLineNo temp2 = currMethod.peek();
			VarWithLineNo temp3 = currPlace.peek();
			
			
			HashMap<Integer, PlaceNode> methodInfoPlace = placeTree.get(temp1.lineNo);
			if (methodInfoPlace != null) {
				PlaceNode pn = methodInfoPlace.get(temp2.lineNo);
				
				//for the child
				if (pn.child != null) {
					PlaceNode pn1 = pn.child;
					
					ClosureDetails cd = closureInfo.get(pn1.toName);
					//System.out.println("I am inside copying the value[Main]: " + cd.fromClass + ":" + cd.fromMethod + ":" + cd.fromplace + ":" + pn1.toName);
					HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(cd.fromClass);
					if (setMethodInfo != null) {
						HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(cd.fromMethod);
						if (placeInfo != null) {
							HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(cd.fromplace);
							if (setDetails != null) {
								HashMap<String, HashSet<String>> rs = setDetails.get("RS");
								HashMap<String, HashSet<String>> crs = setDetails.get("CRS");
								HashMap<String, HashSet<String>> ws = setDetails.get("WS");
								HashMap<String, HashSet<String>> ovs = setDetails.get("OVS");
								
								if (rs != null && crs != null && ws != null && ovs != null) {
									
									//for the ovs only
									HashSet<String> set11 = ovs.get("global-ovs");
									
									Iterator it = rs.entrySet().iterator();
									
									while (it.hasNext()) {
										Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it.next();
										
										HashSet<String> set1 = crs.get(phase3.getKey());
										HashSet<String> set2 = ws.get(phase3.getKey());
										
										HashSet<String> set3 = rs.get(phase3.getKey());
										
										if (set1 != null && set2 != null && set3 != null) {
											set1.removeAll(set2);
	
											set1.addAll(set3);
											
											HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo1 = setInfo.get(temp1.lineNo);
											if (setMethodInfo1 != null) {
												HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo1 = setMethodInfo1.get(temp2.lineNo);
												if (placeInfo1 != null) {
													HashMap<String, HashMap<String, HashSet<String>>> setDetails1 = placeInfo1.get(temp3.lineNo);
													if (setDetails1 != null) {
														HashMap<String, HashSet<String>> crsMethod = setDetails1.get("CRS");
														HashMap<String, HashSet<String>> ovsMethod = setDetails1.get("OVS");
														
														HashSet<String> set4 = crsMethod.get(phase3.getKey());
														if (set4 != null) {
															set4.addAll(set1);
														}
														
														HashSet<String> set12 = ovsMethod.get("global-ovs");
														if (set12 != null) {
															set12.addAll(set11);
														}
														
													}
												}
											}
										}
										
									}
								}
								
							}
						}
					}
					
					
					//for the siblings
					while (pn1.sibling != null) {
						pn1 = pn1.sibling;
						
						cd = closureInfo.get(pn1.toName);
						
						setMethodInfo = setInfo.get(cd.fromClass);
						if (setMethodInfo != null) {
							HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(cd.fromMethod);
							if (placeInfo != null) {
								HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(cd.fromplace);
								if (setDetails != null) {
									HashMap<String, HashSet<String>> rs = setDetails.get("RS");
									HashMap<String, HashSet<String>> crs = setDetails.get("CRS");
									HashMap<String, HashSet<String>> ws = setDetails.get("WS");
									HashMap<String, HashSet<String>> ovs = setDetails.get("OVS");
									
									if (rs != null && crs != null && ws != null && ovs != null) {
										
										//for the ovs only
										HashSet<String> set11 = ovs.get("global-ovs");
										
										Iterator it = rs.entrySet().iterator();
										
										while (it.hasNext()) {
											Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it.next();
											
											HashSet<String> set1 = crs.get(phase3.getKey());
											HashSet<String> set2 = ws.get(phase3.getKey());
											
											HashSet<String> set3 = rs.get(phase3.getKey());
											
											if (set1 != null && set2 != null && set3 != null) {
												set1.removeAll(set2);
												
												
												set1.addAll(set3);
												
												HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo1 = setInfo.get(temp1.lineNo);
												if (setMethodInfo1 != null) {
													HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo1 = setMethodInfo1.get(temp2.lineNo);
													if (placeInfo1 != null) {
														HashMap<String, HashMap<String, HashSet<String>>> setDetails1 = placeInfo1.get(temp3.lineNo);
														if (setDetails1 != null) {
															HashMap<String, HashSet<String>> crsMethod = setDetails1.get("CRS");
															HashMap<String, HashSet<String>> ovsMethod = setDetails1.get("OVS");
															
															HashSet<String> set4 = crsMethod.get(phase3.getKey());
															if (set4 != null) {
																set4.addAll(set1);
															}
															
															HashSet<String> set12 = ovsMethod.get("global-ovs");
															if (set12 != null) {
																set12.addAll(set11);
															}
															
														}
													}
												}
											}
											
										}
									}
									
								}
							}
						}
					}
					
				}
			}
			
			LinkedList<ClassInfo> fi = classDetails.get(temp1.name);
			if (fi != null) {
				Iterator it = fi.iterator();
				while (it.hasNext()) {
					ClassInfo ci = (ClassInfo)it.next();
					if (ci.name.equalsIgnoreCase(temp2.name)) {
						//ci.classNo = temp1.lineNo; //System.out.println("the line no1M: " + ci.classNo );
						//ci.methodNo = temp2.lineNo; //System.out.println("the line no2M: " + ci.methodNo + ":" + temp3.lineNo);
					}
				}
			}
			
			
			//add the code here-initial ones will be updated with all the summary
			HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
			if (methodInfo != null) {
				HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
				if (lineInfo != null) {
					lineInfo.put(temp3.lineNo, lastGraphInfo.peek());
				}
			}
        }
        
        
        /* the printer code */
        /*
    	VarWithLineNo temp1 = currClass.peek();
    	VarWithLineNo temp2 = null;
    	VarWithLineNo temp3 = null;
    	boolean goThrough = true;
    	//if ((temp1.name.equalsIgnoreCase("Histogram") || temp1.name.equalsIgnoreCase("test") || temp1.name.equalsIgnoreCase("$Closure$0"))) {
    		if (currMethod.size() > 0) {
    			temp2 = currMethod.peek();
    		} 
    		else {
    			goThrough = false;
    		}
    		if (currPlace.size() > 0) {
    		 temp3 = currPlace.peek();
    		}
    		else {
    			goThrough = false;
    		}
    	//}
    	//System.out.println("I AM PRINTING: " + temp1.lineNo +":"+ temp2.lineNo +":"+ temp3.lineNo +":" + lineNo);
    	System.out.println("The Graph details: ");
    	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
    	if (methodInfo != null && goThrough) {
    		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
    		if (lineInfo != null) { int lineNo1 = lineNo;
    			HashMap<String, LinkedList<EdgeRep>> varInfo = lastGraphInfo.peek(); lineInfo.put(temp3.lineNo, lastGraphInfo.peek());
    			if (varInfo != null) {
    				System.out.println("For the Method: " + temp2.name);
    				Iterator it = varInfo.entrySet().iterator();
    	        	while (it.hasNext()) {
    	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
    	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
    	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
    	        		Iterator it1 = edgeIncl.iterator();
    	        		while (it1.hasNext())
    	        		{
    	        			EdgeRep er = (EdgeRep)it1.next();
    	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
    	        					+ er.fieldName + " EDGE NAME:" + er.edgeName);
    	        		}
    	        	}
    			}
    		}
    	}
    	
    	
    	//the sets
    	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
    	if (setMethodInfo != null &&  goThrough) {
    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
    		if (placeInfo != null) {
    			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
    			if (setDetails != null) {
    				System.out.println();
		    		System.out.println("Method's Object Sets:");
		    		
		    		System.out.println("Read Set:");
		    		HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
		    		if (readSet != null) {
		    			Iterator it3 = readSet.entrySet().iterator();
		    			while (it3.hasNext()) {
		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it3.next();
		        			System.out.println("Object: " + pair.getKey());
		        			HashSet<String> rs = (HashSet<String>)pair.getValue();
		        			
		        			if(rs != null) {
		        				Iterator it4 = rs.iterator();
		        				
		        				while (it4.hasNext()) {
		            				String str = (String)it4.next();
		            				System.out.print(" {" + str +"} ");
		            			}
		            			System.out.println("");
		        			}
		    			}
		    		}
		    		
		    		System.out.println("");
		    		
		    		System.out.println("Cumulative Read Set:");
		    		HashMap<String, HashSet<String>> creadSet = setDetails.get("CRS");
		    		if (creadSet != null) {
		    			Iterator it3 = creadSet.entrySet().iterator();
		    			while (it3.hasNext()) {
		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it3.next();
		        			System.out.println("Object: " + pair.getKey());
		        			HashSet<String> rs = (HashSet<String>)pair.getValue();
		        			
		        			if(rs != null) {
		        				Iterator it4 = rs.iterator();
		        				
		        				while (it4.hasNext()) {
		            				String str = (String)it4.next();
		            				System.out.print(" {" + str +"} ");
		            			}
		            			System.out.println("");
		        			}
		    			}
		    		}
		    		
		    		System.out.println("");
		    		
		    		System.out.println("Must Write Set: ");
		    		HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
		    		if (writeSet != null) {
		    			Iterator it1 = writeSet.entrySet().iterator();
		    			
		    			while (it1.hasNext()) {
		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it1.next();
		        			System.out.println("Object: " + pair.getKey());
		        			HashSet<String> ws = (HashSet<String>)pair.getValue();
		        			
		        			if(ws != null) {
		        				Iterator it2 = ws.iterator();
		            			while (it2.hasNext()) {
		            				String str = (String)it2.next();
		            				System.out.print(" {" + str +"} ");
		            			}
		            			System.out.println("");
		        			}
		    			}
		    		}
		    		
		    		System.out.println("");
		    		System.out.println("May Write Set: ");
		    		HashMap<String, HashSet<String>> mWriteSet = setDetails.get("MWS");
		    		if (mWriteSet != null) {
		    			Iterator it5 = mWriteSet.entrySet().iterator();
		    			while (it5.hasNext()) {
		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it5.next();
		        			System.out.println("Object: " + pair.getKey());
		        			HashSet<String> mws = (HashSet<String>)pair.getValue();
		        			
		        			if(mws != null) {
		            			Iterator it6 = mws.iterator();
		            			while (it6.hasNext()) {
		            				String str = (String)it6.next();
		            				System.out.print(" {" + str +"} ");
		            			}
		            			System.out.println("");
		        			}
		    			}
		    		}
		    		
		    		
		    		System.out.println("");
		    		
		    		System.out.println("OV Set: ");
		    		HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
		    		if (ovSet != null) {
		    			Iterator it1 = ovSet.entrySet().iterator();
		    			
		    			while (it1.hasNext()) {
		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it1.next();
		        			System.out.println("Object: " + pair.getKey());
		        			HashSet<String> ovs = (HashSet<String>)pair.getValue();
		        			
		        			if(ovs != null) {
		        				Iterator it2 = ovs.iterator();
		            			while (it2.hasNext()) {
		            				String str = (String)it2.next();
		            				System.out.print(" {" + str +"} ");
		            			}
		            			System.out.println("");
		        			}
		    			}
		    		}
		    		
    			}
    		}
    	}
    	System.out.println("-----------------------------------end of method---------------------------------");
    	*/
    	/* the printer code */
        
        
        if (currMethod.size() > 0) {
        	currMethod.pop();
        }
        if (currPlace.size() > 0) {
        	currPlace.pop();
        }
        if (lastGraphInfo.size() > 0) {
        	lastGraphInfo.removeAllElements();
        }
        recVarInfo = null;
		/* Nobita code */
    }

    // ////////////////////////////////
    // Stmt
    // ////////////////////////////////
    
    public static void catchAndThrowAsX10Exception(CodeWriter w) {
        String TEMPORARY_EXCEPTION_VARIABLE_NAME = Name.makeFresh("exc").toString();
        w.write("catch (" + JAVA_LANG_THROWABLE + " " + TEMPORARY_EXCEPTION_VARIABLE_NAME + ") {");
        w.newline(4);
        w.begin(0);
        w.write("throw " + X10_RUNTIME_IMPL_JAVA_THROWABLEUTILS + "." + ENSURE_X10_EXCEPTION + "(" + TEMPORARY_EXCEPTION_VARIABLE_NAME + ");");
        w.end();
        w.newline();
        w.writeln("}");
    }

    @Override
    public void visit(Block_c n) {
    	
    	/* Nobita code */
    	lineNo++;
    	boolean block_ifBlock = false;
    	boolean block_theAlter = false;
    	if(ifBlock) {
    		block_ifBlock = true;
    		ifBlock = false;
    		if(theAlter) {
    			block_theAlter = true;
    			theAlter = false;
    		}
    	}
    	/* Nobita code */
    	
        String s = Emitter.getJavaImplForStmt(n, tr.typeSystem());
        if (s != null) {
            w.write("try {"); // XTENLANG-2686: handle Java exceptions inside @Native block
            w.newline(4);
            w.begin(0);
            w.write(s);
            w.end();
            w.newline();
            w.write("}"); // XTENLANG-2686
            w.newline();
            catchAndThrowAsX10Exception(w); // XTENLANG-2686
        } else {
            n.translate(w, tr);
        }
        
        
        /* Nobita code */
        //this piece of code is for If_c else part
        lineNo++;
        if(block_theAlter && (currClass.size() > 0) && (currMethod.size() > 0)) {
        	//getting the class, the place and the method
        	VarWithLineNo temp1 = currClass.peek();
        	VarWithLineNo temp2 = currMethod.peek();
        	VarWithLineNo temp3 = currPlace.peek();
        	
        	
        	// the onject
        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
        	if (methodInfo != null) {
        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
        		if (lineInfo != null) {
        			if (lastGraphInfo.size() > 0) {
        				lineInfo.put(lineNo, lastGraphInfo.pop());
        			}
        			
        			HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
        			if(varInfo != null) {
        				HashMap<String, LinkedList<EdgeRep>> updates = deepCopy(varInfo);
        				if (updates != null) {
        					theIf.add(updates);
        				}
        			}
        			
        			HashMap<String, LinkedList<EdgeRep>> backUp = theIf1.peek();
        			if (backUp != null) {
        				lastGraphInfo.push(backUp);
        			}
        		}
        	}
        	
        	//the set
        	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	if (setMethodInfo != null) {
        		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        		if (placeInfo != null) {
        			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
        			if (setDetails != null) {
        				HashMap<String, HashMap<String, HashSet<String>>> setUpdate = deepCopySetIf(setDetails);
	    				if (setUpdate != null) {
	    					theIfSet.add(setUpdate);
	    				}
        			}
        			
        			HashMap<String, HashMap<String, HashSet<String>>> setBackUp = theIfSetBase.peek();
	    			if (setBackUp != null) {
	    				placeInfo.put(temp3.lineNo, setBackUp);
	    			}
        		}
        	}
        	
        }

        /* Nobita code */
        
    }

    @Override
    public void visit(StmtSeq_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(SwitchBlock_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(Assert_c n) {
        if (!tr.job().extensionInfo().getOptions().assertions)
            return;

        if (useJavaAssertion) {
            n.translate(w, tr);
        } else {
            Expr cond = ((Assert_c) n).cond();
            Expr errorMessage = ((Assert_c) n).errorMessage();

            w.write("if (!" + X10_RUNTIME_IMPL_JAVA_RUNTIME + ".DISABLE_ASSERTIONS && ");
            w.write("!(");
            tr.print(n, cond, w);
            w.write(")");
            w.write(") {");
            w.write("throw new x10.lang.AssertionError(");

            if (errorMessage != null) {
                w.write("java.lang.String.valueOf(");
                tr.print(n, errorMessage, w);
                w.write(")");
            }

            w.write(");");
            w.write("}");
        }

    }

    @Override
    public void visit(AssignPropertyCall_c n) {
        // TODO: initialize properties in the Java constructor
        List<X10FieldInstance> definedProperties = n.properties();
        List<Expr> arguments = n.arguments();
        int aSize = arguments.size();
        assert (definedProperties.size() == aSize);

        for (int i = 0; i < aSize; i++) {
            Expr arg = arguments.get(i);
            X10FieldInstance fi = definedProperties.get(i);
            w.write("this.");
            w.write(Emitter.mangleToJava(fi.name()));
            w.write(" = ");
            er.coerce(n, arg, fi.type());
            w.write(";");
            w.newline();
        }
    }

    @Override
    public void visit(Branch_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(Case_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(Catch_c n) {
        w.write("catch (");
        n.printBlock(n.formal(), w, tr);
        w.write(")");
        n.printSubStmt(n.body(), w, tr);
    }

    @Override
    public void visit(X10ConstructorCall_c c) {
        ContainerType ct = c.constructorInstance().container();
        if (isSplittable(ct)
            || ct.name().toString().startsWith(ClosureRemover.STATIC_NESTED_CLASS_BASE_NAME) // is this needed?
            ) {
            TypeSystem ts = tr.typeSystem();
            Expr target = c.target();
            if (target == null || target instanceof Special) {
                if (c.kind() == ConstructorCall.SUPER) {
                    if (Emitter.isNativeRepedToJava(ct) || Emitter.isNativeClassToJava(ct)) {
                        return;
                    }
                    w.write("/*super.*/");
                } else {
                    w.write("/*this.*/");
                }
            } else {
                if (c.kind() == ConstructorCall.SUPER) {
                    target.translate(w, tr);
                    w.write(".");
                    // invoke constructor for non-virtual call directly
                    String ctorName = CONSTRUCTOR_METHOD_NAME(ct.toClass().def()); 
                    w.write(ctorName);
                    printConstructorArgumentList(c, c, c.constructorInstance(), null, false);
                    w.write(";");
                    return;
                }
                target.translate(w, tr);
                w.write(".");
            }
            w.write(CONSTRUCTOR_METHOD_NAME(ct.toClass().def()));
            printConstructorArgumentList(c, c, c.constructorInstance(), null, false);
            w.write(";");
            return;
        }
        printConstructorCallForJavaCtor(c);
    }

    private void printConstructorCallForJavaCtor(X10ConstructorCall_c c) {
        if (c.qualifier() != null) {
            tr.print(c, c.qualifier(), w);
            w.write(".");
        }
        w.write(c.kind() == ConstructorCall.THIS ? "this" : "super");
        printConstructorArgumentList(c, c, c.constructorInstance(), null, true);
        w.write(";");
    }

    private void printConstructorArgumentList(Node_c c, X10ProcedureCall p, X10ConstructorInstance mi, Type type, boolean forceParams) {
    	
    	w.write("(");
        w.begin(0);

        if (forceParams) {
        X10ClassType ct = mi.container().toClass();
        List<Type> ta = ct.typeArguments();
        boolean isJavaNative = type != null ? Emitter.isNativeRepedToJava(type) : false;
        if (ta != null && ta.size() > 0 && !isJavaNative) {
            printArgumentsForTypeParams(ta, p.arguments().size() == 0);
        }
        }

        List<Expr> l = p.arguments();
        for (int i = 0; i < l.size(); i++) {
            Expr e = l.get(i);
            if (i < mi.formalTypes().size()) { // FIXME This is a workaround
                Type castType = mi.formalTypes().get(i);
                Type defType = mi.def().formalTypes().get(i).get();
                TypeSystem xts = tr.typeSystem();
                if (isString(e.type()) && !isString(castType)) {

                    w.write("(");
                    er.printType(castType, 0);
                    w.write(")");

                    if (xts.isParameterType(castType)) {
                        w.write(X10_RTT_TYPES);
                        w.write(".conversion(");
                        new RuntimeTypeExpander(er, Types.baseType(castType)).expand(tr);
                        w.write(",");
                    } else {
                        w.write("(");
                    }
                    c.print(e, w, tr);
                    w.write(")");
                } else if (useSelfDispatch && !castType.typeEquals(e.type(), tr.context())) {
                    w.write("(");
                    if (needExplicitBoxing(e.type()) && isBoxedType(defType)) {
                        er.printBoxConversion(e.type());
                    } else {
                        // TODO:CAST
                        w.write("(");
                        // XTENLANG-2895 use erasure to implement co/contra-variance of function type
                        // XTENLANG-3259 to avoid post-compilation error with Java constructor with Comparable parameter.
                        er.printType(castType, (xts.isFunctionType(castType) || Emitter.isNativeRepedToJava(castType)) ? 0 : PRINT_TYPE_PARAMS);
                        w.write(")");
                    }
                    w.write("(");       // printBoxConvesion assumes parentheses around expression
                    c.print(e, w, tr);
                    w.write(")");
                    w.write(")");
                } else {
                    if (needExplicitBoxing(castType) && defType.isParameterType()) {
                        er.printBoxConversion(castType);
                        w.write("(");
                        c.print(e, w, tr);
                        w.write(")");
                    } else {
                        c.print(e, w, tr);
                    }
                }
            } else {
                c.print(e, w, tr);
            }
            if (isMutableStruct(e.type())) {
                w.write(".clone()");
            }

            if (i != l.size() - 1) {
                w.write(", ");
            }
        }

        printExtraArgments(mi);

        w.end();
        w.write(")");
    }

    @Override
    public void visit(Empty_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(Eval_c n) {
    	
    	/* Nobita code */
    	lineNo++;
    	/* Nobita code */
    	
        boolean semi = tr.appendSemicolon(true);
        Expr expr = n.expr();
        // XTENLANG-2000
        if (expr instanceof X10Call) {
            // support for back-end method inlining
            if (er.isMethodInlineTarget(tr.typeSystem(), ((X10Call) expr).target().type())
                    && ((X10Call) expr).methodInstance().name() == ClosureCall.APPLY) {
                w.write(X10_RUNTIME_IMPL_JAVA_EVALUTILS + ".eval(");
                n.print(expr, w, tr);
                w.write(")");
            } else if (er.isMethodInlineTarget(tr.typeSystem(), ((X10Call) expr).target().type())
                    && ((X10Call) expr).methodInstance().name() == SettableAssign.SET) {
                n.print(expr, w, tr);
            }
            // support for @Native
            else if (!expr.type().isVoid()
                    && Emitter.getJavaImplForDef(((X10Call) expr).methodInstance().x10Def()) != null) {
                w.write(X10_RUNTIME_IMPL_JAVA_EVALUTILS + ".eval(");
                n.print(expr, w, tr);
                w.write(")");
            } else {
                n.print(expr, w, tr);
            }
        }
        // when expr is StatementExpression(Assignment ||
        // [Pre/Post][De/In]crementExpression || MethodInvocation ||
        // ClassInstanceCreationExpression)
        else if (expr instanceof ClosureCall || expr instanceof Assign || expr instanceof Unary
                || expr instanceof X10New) {
            n.print(expr, w, tr);
        }
        // not a legal java statement
        else {
            w.write(X10_RUNTIME_IMPL_JAVA_EVALUTILS + ".eval(");
            n.print(expr, w, tr);
            w.write(")");
        }
        if (semi) {
            w.write(";");
        }
        tr.appendSemicolon(semi);
    }

    @Override
    public void visit(If_c n) {
    	
    	/* Nobita code */
    	boolean alter = false;
    	lineNo++;
    	if ((currClass.size() > 0) && (currMethod.size() > 0)) {
    		
    		//getting the class, the place and the method
        	VarWithLineNo temp1 = currClass.peek();
        	VarWithLineNo temp2 = currMethod.peek();
        	VarWithLineNo temp3 = currPlace.peek();
    		
    		ifBlock = true;
    		
    		if((n.alternative()) != null) {
	    		alter = true;
	    		theAlter = true;
	    	}
    		
    		//the graph
    		HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
        	if (methodInfo != null) {
        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
        		if (lineInfo != null) {
        			if (lastGraphInfo.size() > 0) {
        				lineInfo.put(lineNo, lastGraphInfo.peek());
        			}
        			
        			HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
        			if(varInfo != null) {
        				HashMap<String, LinkedList<EdgeRep>> backUp = deepCopy(varInfo);
	    				if (backUp != null) {
	    					theIf1.add(backUp);
	    				}
        			}
        			
        		}
        	}
        	
        	//the sets
        	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	if (setMethodInfo != null) {
        		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        		if (placeInfo != null) {
        			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
        			if (setDetails != null) {
        				HashMap<String, HashMap<String, HashSet<String>>> setBackUp = deepCopySetIf(setDetails);
	    				if (setBackUp != null) {
	    					theIfSetBase.add(setBackUp);
	    				}
        			}
        		}
        	}
        	
        	
    	}
    	/* Nobita code */
    	
        n.translate(w, tr);
        
        /* Nobita code */
        if ((currClass.size() > 0) && (currMethod.size() > 0)) {
        	 
        	//getting the class, the place and the method
        	VarWithLineNo temp1 = currClass.peek();
        	VarWithLineNo temp2 = currMethod.peek();
        	VarWithLineNo temp3 = currPlace.peek();
        	
        	lineNo++;
        	
        	HashMap<String, LinkedList<EdgeRep>> merger1 = null;
            HashMap<String, HashMap<String, HashSet<String>>> setMerger1 = null;
            
            if(alter) {
            	merger1 = theIf.pop();
            	theIf1.pop();
            	setMerger1 = theIfSet.pop();
            	theIfSetBase.pop();
            }
            else {
            	merger1 = theIf1.pop();
            	setMerger1 = theIfSetBase.pop(); 
            }
            
            //the graph
            HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
        	if (methodInfo != null) {
        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
        		if (lineInfo != null) {
        			if (lastGraphInfo.size() > 0) {
        				lineInfo.put(lineNo, lastGraphInfo.pop());
        			}
        			
        			HashMap<String, LinkedList<EdgeRep>> merger2 = lineInfo.get(lineNo);
        			
        			if (merger1 != null && merger2 != null) {
	    				HashMap<String, LinkedList<EdgeRep>> mergeBack = payBack1 (merger2, merger1);
	    				if (mergeBack != null) {
	    					lineInfo.replace(lineNo, mergeBack);
	    					lastGraphInfo.push(mergeBack);
	    				}
	    			}	
        		}
        	}
        	
        	//the sets
        	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	if (setMethodInfo != null) {
        		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        		if (placeInfo != null) {
        			HashMap<String, HashMap<String, HashSet<String>>> setMerger2 = placeInfo.get(temp3.lineNo);
        			if (setMerger1 != null && setMerger2 != null) {
	    				HashMap<String, HashMap<String, HashSet<String>>> mergeBackSet = payBackSet1(setMerger2, setMerger1, alter);
	    				if (mergeBackSet != null) {
	    					placeInfo.replace(temp3.lineNo, mergeBackSet);
	    				}
	    			}
        		}
        	}
            
        }
        
        /* the printer code */
        /*
    	VarWithLineNo temp1 = currClass.peek();
    	VarWithLineNo temp2 = null;
    	VarWithLineNo temp3 = null;
    	boolean goThrough = true;
    	//if ((temp1.name.equalsIgnoreCase("Histogram") || temp1.name.equalsIgnoreCase("test"))) {
    		if (currMethod.size() > 0) {
    			temp2 = currMethod.peek();
    		} 
    		else {
    			goThrough = false;
    		}
    		if (currPlace.size() > 0) {
    		 temp3 = currPlace.peek();
    		}
    		else {
    			goThrough = false;
    		}
    	//}
    	//System.out.println("I AM PRINTING: " + temp1.lineNo +":"+ temp2.lineNo +":"+ temp3.lineNo +":" + lineNo);
    	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
    	if (methodInfo != null && goThrough) {
    		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
    		if (lineInfo != null) {
    			HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
    			if (varInfo != null) {
    				System.out.println("GRAPH AFTER IF CONDITION: " + lineNo);
    				Iterator it = varInfo.entrySet().iterator();
    	        	while (it.hasNext()) {
    	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
    	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
    	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
    	        		Iterator it1 = edgeIncl.iterator();
    	        		while (it1.hasNext())
    	        		{
    	        			EdgeRep er = (EdgeRep)it1.next();
    	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
    	        					+ er.fieldName);
    	        		}
    	        	}
    			}
    		}
    		System.out.println("--------------------------------------------------end of if cond--------------------------------------");
    	}
    	*/
    	/* the printer code */
        /* Nobita code */
    }

    @Override
    public void visit(Labeled_c n) {
        Stmt statement = n.statement();
        if (statement instanceof Block_c) {
            w.write(n.labelNode() + ": ");
            w.write("{");
            w.newline(4);
            w.begin(0);
            Block_c block = (Block_c) statement;
            for (Stmt s : block.statements()) {
                tr.print(n, s, w);
            }
            w.end();
            w.newline();
            w.write("}");
        } else {
            w.write(n.labelNode() + ": ");
            tr.print(n, statement, w);
        }
    }

    @Override
    public void visit(X10LocalDecl_c n) {

        // same with FieldDecl_c#prettyPrint(CodeWriter w, PrettyPrinter tr)
    	
    	/* Nobita code */
    	lineNo++;
    	
    	//this code is for second pass - code generation
    	if (iterationCount > iterationCountSupp && (n.init() instanceof X10Cast_c || n.init() instanceof X10Call_c)) {
    		//System.out.println("YES I AM IN LOCALDEC - CAST" + n.name().toString());
    		Expr expr = n.init();
    		
    		if (n.init() instanceof X10Cast_c) {
    			X10Cast_c ca = (X10Cast_c)n.init();
    			expr = ca.expr();
    		}
    		
    		if (expr instanceof X10Call) {
    			//System.out.println("YES I AM IN LOCALDEC - CALL");
    			X10Call_c call = (X10Call_c)expr;
    			
    			if (call.name().toString().equals("evalAt")) {

    				savedObj = new Stack<String>();
    				int closureLineNo = lineNo + 1;
    				String closureName = lineClosure.get(closureLineNo);
    				
    				HashMap <String, String> varClosure = closureVar.get(closureName);
    				HashMap<String, ForClosureObject> objClosure = closureObj.get(closureName);
    				
    				if (varClosure != null) {
    					
    					Iterator it = varClosure.entrySet().iterator();
    					while (it.hasNext()) {
    						Map.Entry<String, String> phase3 = (Map.Entry<String, String>)it.next();
    						
    						if (!(phase3.getValue().equalsIgnoreCase("multiple"))) {
    							
    							String objName = phase3.getValue();
    							
    							if (!(savedObj.contains(objName))) {
    								
    								savedObj.push(objName);
    								ForClosureObject fco = objClosure.get(objName);
    								
    								if (fco != null) {
    									LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
    									
    									Iterator it1 = llFCOF.iterator();
    									while (it1.hasNext()) {
    										ForClosureObjectField fcof = (ForClosureObjectField)it1.next();
    										
    										if (fcof.tempStoredName.equalsIgnoreCase("NR")) {
    											PrettyPrinterTemporary(fcof.fieldObjName, objClosure);
    										}
    										else {
    											w.newline();
    											String writerType = fcof.fieldType;
    											if (fcof.fieldType.equalsIgnoreCase("x10.lang.Long")) {
    												writerType = "long";
    											}
    											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Float")) {
    												writerType = "float";
    											}
    											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Double")) {
    												writerType = "double";
    											}
    											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Char")) {
    												writerType = "char";
    											}
    											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.String")) {
    												writerType = "java.lang.String";
    											}
    											else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Boolean")) {
    												writerType = "boolean";
    											}
    											else {
    												String tempTyCh = fcof.fieldType;
    												String resultStr = " ";
    												int j = tempTyCh.length()-1;
    												for(int i = 0; i<=j ; i++) {
    													if (i <= j-3) {
    														int k = i;
    														if (tempTyCh.charAt(i) == 'l' && tempTyCh.charAt(++k) == 'a' && 
    																tempTyCh.charAt(++k) == 'n' && tempTyCh.charAt(++k) == 'g') {
    															resultStr = resultStr + "core";
    															i = i +3;
    														}
    														else {
    															
    															if (tempTyCh.charAt(i) == '[') {
    																resultStr = resultStr + "<";
    															}
    															else if(tempTyCh.charAt(i) == ']') {
    																resultStr = resultStr + ">";
    															}
    															else {
    																resultStr = resultStr + tempTyCh.charAt(i);
    															}
    														}
    													}
    													else {
    														if (tempTyCh.charAt(i) == '[') {
    															resultStr = resultStr + "<";
    														}
    														else if(tempTyCh.charAt(i) == ']') {
    															resultStr = resultStr + ">";
    														}
    														else {
    															resultStr = resultStr + tempTyCh.charAt(i);
    														}
    													}
    												}
    												
    												writerType = resultStr;
    											}
    											
    											
    											w.write("final ");
    											w.write(writerType);
    											w.write(" ");
    											w.write(fcof.tempStoredName);
    											w.write(" = ");
    											w.write(fco.varName);
    											w.write(".");
    											w.write(fcof.fieldName);
    											w.write(";");
    											w.newline();
    											
    										}
    									}
    									
    								}
    							}	
    						}
    					}
    				}
    				
    				savedObj = new Stack<String>();
    			}
    		}
    	}
    	/* Nobita code */
    	
        boolean printSemi = tr.appendSemicolon(true);
        boolean printType = tr.printType(true);

        tr.print(n, n.flags(), w);
        if (printType) {
            if (supportTypeConstraintsWithErasure) {
                er.printType(n.type().type(), 0);
            } else
            tr.print(n, n.type(), w);
            w.write(" ");
        }
        tr.print(n, n.name(), w);

        //System.out.println("Printing the flag of the variable: " + n.flags().toString());
        
        if (n.init() != null) {
            w.write(" = ");

            // X10 unique
            er.coerce(n, n.init(), n.type().type());
            
	        /* Nobita code */
	        if ((currClass.size() > 0) && (currMethod.size() > 0) && (n.type().nameString()) != null) {
	        	
	        	//getting the class and the method
	        	VarWithLineNo temp1 = currClass.peek();
	        	VarWithLineNo temp2 = currMethod.peek();
	        	VarWithLineNo temp3 = currPlace.peek();
	        	
	        	String varType = n.type().nameString();
	        	if (varType.equalsIgnoreCase("Place") || varType.equalsIgnoreCase("PlaceGroup")) {
	        		atCall = true;
	        		//System.out.println("Printing the line no beofre at: " + lineNo);
	        	}
	        	//System.out.println("The Type of the local var is: " + n.type().nameString());
	        	if((varType != null) && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
                		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
                		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
                		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
	        		//System.out.println("The Type of the local var is[inside]: " + n.type().nameString());
	        		HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
	        		if (methodInfo != null) {
	        			HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
	        			if (lineInfo != null) {
	        				if (lastGraphInfo.size() > 0) {
	        					lineInfo.put(lineNo, lastGraphInfo.pop()); //check for whether back needs to take for second pass also
	        					//update modifier boolean
	        				} 
	        				else {
	        					lineInfo.put(lineNo, new HashMap<String, LinkedList<EdgeRep>>());
		       					//update modifier boolean
	        				}
	        				
	        				//System.out.println("THE LINE NO at localdecl:" + temp1.lineNo +":"+temp2.lineNo+":"+lineNo);
	        				HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo); 				
	        				if(varInfo != null) {
	        					if(!(varInfo.containsKey(n.name().toString()))) {
                            		varInfo.put(n.name().toString(), new LinkedList<EdgeRep>());
                            		//update modifier boolean
                            	}
	        					
	        					///////////////////////////////////
	        					String path = "";
	        					String fldName = "";
                				String varName = "";
                				String rhsVar = "";
	        					if (n.init() instanceof X10New_c) {
	        						path = "X10New_c";
	        					}
	        					else if(n.init() instanceof Local_c) {
	        						Local_c tempLocal = (Local_c)n.init();
	        						path = "Local_c";
	        						rhsVar = tempLocal.name().toString();
	        					}
	        					else if (n.init() instanceof Field_c) {
	        						Field_c tempField = (Field_c)n.init();
	        						if (temp2.name.equalsIgnoreCase("operator()")) {
	        							//System.out.println(tempField.target().toString() + ":" + tempField.name().toString());
	        							if (!(tempField.target() instanceof Field_c)) {
	        								path = "Local_c";
	        								rhsVar = tempField.name().toString();
	        							}
	        							else {
	        								path = "Field_c";
	        								fldName = tempField.name().toString();
	            							Field_c tempField1 = (Field_c)tempField.target();
	            							varName = tempField1.name().toString();
	        							}
	        						} 
	        						else {
	        							path = "Field_c";
	        							if (tempField.target() instanceof Special_c) {
	        								varName = "this";
	        							}
	        							else {
	        								varName = tempField.target().toString();
	        							}
	        							fldName = tempField.name().toString();
	        						}
	        					}
	        					else if (n.init() instanceof NullLit_c) {
	        						path = "NullLit_c";
	        					}
	        					
	        					
	        					
	        					//System.out.println("the class name:  " + n.init().getClass().getSimpleName());
	        					
	        					//for new
	        					if (path.equalsIgnoreCase("X10New_c")) {
	        						String objName = "obj"+lineNo;
	        						
	        						//the object
	        						HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo2 = objInfo.get(temp1.lineNo);
	        						if (methodInfo2 != null) {
	        							HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo2.get(temp2.lineNo);
	        							if (placeInfo != null) {
	        								HashMap<String, ObjNode> objDetail = placeInfo.get(temp3.lineNo);
	        								if (objDetail != null) {
	        									if (!objDetail.containsKey(objName)) {
	        										ObjNode temp = new ObjNode(objName, varType);
	        										objDetail.put(objName, temp);
	        										//update modifier boolean
	        									}
	        								}
	        							}
	        						}
	        						
	        						//the graph
	        						LinkedList<EdgeRep> edgeIncl = varInfo.get(n.name().toString());
                                	if (edgeIncl != null) {
                                		Iterator it = edgeIncl.iterator();
                                		boolean found = false;
                                		while (it.hasNext()) {
                                			EdgeRep er1 = (EdgeRep)it.next();
                                			
                                			if(er1.desName.equalsIgnoreCase(objName)) {
                                				found = true;
                                				break;
                                			}
                                		}
                                		if (!found) {
                                			EdgeRep edgeInfo = new EdgeRep("P",objName);
                                			edgeIncl.addLast(edgeInfo);
                                			//update modifier boolean
                                		}
                                	}
                                	
                                	//inter-procedural analysis code
                                	X10New_c tempNew = (X10New_c)n.init();
                                	String consType = tempNew.type().name().toString();
                                	
                                	//this is for the object fields of the variable
                                	//TODO: This code needs to be removed
                                	/* LinkedList<ClassInfo> ll = classDetails.get(consType);
                                	if (ll != null) {
                                		Iterator it = ll.iterator();
                                		
                                		while (it.hasNext()) {
                                			ClassInfo fd = (ClassInfo)it.next();
                                			//System.out.println("the aruns printer: " + fd.name);
                                			if (fd.classifier.equalsIgnoreCase("field")) {
		                                		if(!((fd.type.equalsIgnoreCase("Long")) || (fd.type.equalsIgnoreCase("Float")) || (fd.type.equalsIgnoreCase("String")) || (fd.type.equalsIgnoreCase("FileReader")) || (fd.type.equalsIgnoreCase("Printer")) || (fd.type.equalsIgnoreCase("Random")) || (fd.type.equalsIgnoreCase("FileWriter")) || 
		                                        		(fd.type.equalsIgnoreCase("Double")) || (fd.type.equalsIgnoreCase("Char")) || (fd.type.equalsIgnoreCase("PlaceGroup")) || (fd.type.equalsIgnoreCase("File")) || (fd.type.equalsIgnoreCase("FailedDynamicCheckException")) || (fd.type.equalsIgnoreCase("FinishState")) || (fd.type.equalsIgnoreCase("LongRange")) ||
		                                        		(fd.type.equalsIgnoreCase("Boolean")) || (fd.type.equalsIgnoreCase("Rail")) || (fd.type.equalsIgnoreCase("Place")) || (fd.type.equalsIgnoreCase("Dist")) || (fd.type.equalsIgnoreCase("ArrayList")) || (fd.type.equalsIgnoreCase("Iterator")) || (fd.type.equalsIgnoreCase("Point")) || (fd.type.equalsIgnoreCase("Int")) ||
		                                        		(fd.type.equalsIgnoreCase("Array")) || (fd.type.equalsIgnoreCase("DistArray")) || (fd.type.equalsIgnoreCase("Region")) || (fd.type.equalsIgnoreCase("GlobalRef")))) {
		                                			
		                                			if(!(varInfo.containsKey(objName))) {
		                                        		varInfo.put(objName, new LinkedList<EdgeRep>());
		                                        		//update modifier boolean
		                                        	}
		                                			
		                                			LinkedList<EdgeRep> edgeInclField = varInfo.get(objName);
		                                			if (edgeInclField != null) {
		                                				EdgeRep edgeInfo = new EdgeRep("F","Obj-null",fd.name);
		                                				edgeInclField.addLast(edgeInfo);
		                                			}
		                                		}
                                			}
                                		}
                                	} */
                                	
                                	
                                	
                                	//the set
                                	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
                                	if (setMethodInfo != null) {
                                		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
                                		if (placeInfo != null) {
                                			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
                                			if (setDetails != null) {
                                				HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
                            					if (readSet != null && !readSet.containsKey(objName)) {
                            						readSet.put(objName, new HashSet<String>());
                            						//update modifier boolean
                            					}
                                        		HashMap<String, HashSet<String>> cumReadSet = setDetails.get("CRS");
                                        		if (cumReadSet != null && !cumReadSet.containsKey(objName)) {
                                        			cumReadSet.put(objName, new HashSet<String>());
                                        			//update modifier boolean
                                        		}
                                        		HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
                                        		if (writeSet != null && !writeSet.containsKey(objName)) {
                                        			writeSet.put(objName, new HashSet<String>());
                                        			//update modifier boolean
                                        		}
                                        		HashMap<String, HashSet<String>> mWriteSet = setDetails.get("MWS");
                                        		if (mWriteSet != null && !mWriteSet.containsKey(objName)) {
                                        			mWriteSet.put(objName, new HashSet<String>());
                                        			//update modifier boolean
                                        		}
                                        		HashMap<String, HashSet<String>> cumWriteSet = setDetails.get("CWS");
                                        		if (cumWriteSet != null && !cumWriteSet.containsKey(objName)) {
                                        			cumWriteSet.put(objName, new HashSet<String>());
                                        			//update modifier boolean
                                        		}
                                        		HashMap<String, HashSet<String>> objectSet = setDetails.get("OS");
                                        		if (objectSet != null && !objectSet.containsKey(objName)) {
                                        			objectSet.put(objName, new HashSet<String>()); 
                                        			//update modifier boolean
                                        		}
                                			}
                                		}
                                	}
                                	
                                	//this is the code for the inter-procedural analysis
                                	X10ProcedureCall tempProdCall = (X10ProcedureCall)tempNew;
                                	List<Expr> l = tempProdCall.arguments();
                                	String uniqValue = "";
                                	for (int i = 0; i < l.size(); i++) {
                                		Expr e = l.get(i);
                                		String parType = "";

                                		if (e.type().name() == null) {
                                    		uniqValue = uniqValue + "|" + "N";
                                    	}
                                    	else {
                                    		String varType1 = e.type().name().toString();
                                    		uniqValue = uniqValue + "|" + typeUniqueID.get(varType1);
                                    		
                                    		//TODO: Check and remove the below stmt
                                    		parType = e.type().name().toString();
                                    	}
                                		
                                	}
                                	
                                	LinkedList<ClassInfo> cill = classDetails.get(consType);
                                	int srcClass = 0;
                                	int srcMethod = 0;
                                	if (cill != null) {
                                		
                                		if(uniqValue.equalsIgnoreCase("")) {
	                                		Iterator it = cill.iterator();
	                                		while (it.hasNext()) {
	                                			ClassInfo ci  = (ClassInfo)it.next();
	                                			if(ci.classifier.equalsIgnoreCase("constructor") && ( ci.uniqueId == null || ci.uniqueId.equalsIgnoreCase(""))) {
	                                				srcClass = ci.classNo;
	                                				srcMethod = ci.methodNo;
	                                				break;
	                                			}
	                                		}
                                		}
                                		else {
                                			Iterator it = cill.iterator();
	                                		while (it.hasNext()) {
	                                			ClassInfo ci  = (ClassInfo)it.next();
	                                			if(ci.classifier.equalsIgnoreCase("constructor") && ci.uniqueId != null && ci.uniqueId.equalsIgnoreCase(uniqValue)) {
	                                				srcClass = ci.classNo;
	                                				srcMethod = ci.methodNo;
	                                				break;
	                                			}
	                                		}
                                		}
                                		
                                		//here call the function to do the merging
                                		
                            	    	HashMap<String, LinkedList<EdgeRep>> varInfoCallee = null;
                            	    	HashMap<String, HashMap<String, HashSet<String>>> setDetailsCaller = null;
                            	    	HashMap<String, ObjNode> objDetails = null;
                            	    	
                            	    	//varInfoCaller
                            	    	//that is varInfo
                            	    	if(!(varInfo.containsKey(objName))) {
                                    		varInfo.put(objName, new LinkedList<EdgeRep>());
                                    	}
                            	    	
                            	    	//varInfoCallee
                            	    	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo3 = graphInfo.get(srcClass);
                            	    	if (methodInfo3 != null) {
                            	    		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> placeInfo2 = methodInfo3.get(srcMethod);
                            	    		if (placeInfo2 != null) {
                            	    			varInfoCallee = placeInfo2.get(srcMethod);
                            	    		}
                            	    	}
                            	    	
                            	    	//setDetailsCaller
                            	    	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo2 = setInfo.get(temp1.lineNo);
                            	    	if (setMethodInfo2 != null) {
                            	    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo2.get(temp2.lineNo);
                            	    		if (placeInfo != null) {
                            	    			setDetailsCaller = placeInfo.get(temp3.lineNo);
                            	    		}
                            	    	}
                            	    	
                            	    	//Object Details
                            	    	HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo4 = objInfo.get(temp1.lineNo);
                            	    	if (methodInfo4 != null) {
                            	    		HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo4.get(temp2.lineNo);
                            	    		if (placeInfo != null) {
                            	    			objDetails = placeInfo.get(temp3.lineNo);
                            	    		}
                            	    	}
                                		
                            	    	if (varInfoCallee != null && objDetails != null && setDetailsCaller != null) {
                            	    		conCallGraphUpdate(varInfo, varInfoCallee, objDetails, objName, "obj-this", lineNo, setDetailsCaller);
                            	    	}
                                	}
	        					}
	        					else if(path.equalsIgnoreCase("Local_c")) {
            							
            							//System.out.println("local var name: " + rhsVar);
            	            			LinkedList<EdgeRep> src = varInfo.get(rhsVar);
            	            			LinkedList<EdgeRep> dest = varInfo.get(n.name().toString());
            	            			
            	            			if (src != null && dest != null) {
            	            				
            	            				//for the global ovs
            	            				if (src.size() > 1) {
            	            					HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
            	            					if (setMethodInfo != null) {
            	            						HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
            	            						if (placeInfo != null) {
            	            							HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
            	            							if (setDetails != null) {
            	            								HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
            	            								if (ovSet != null) {
            	            									HashSet<String> set1 = ovSet.get("global-ovs");
            	            									if (set1 != null) {
            	            										set1.add(rhsVar);
            	            										set1.add(n.name().toString());
            	            									}
            	            								}
            	            							}
            	            						}
            	            					}
            	            				}
            	            				
            	            				
	            	            			Iterator it = src.iterator();
	            	            			while (it.hasNext())
	            	                		{
	            	                			EdgeRep er = (EdgeRep)it.next();
	            	                			
	            	                			Iterator it1 = dest.iterator();
	            	                			boolean found = false;
	            	                			while (it1.hasNext()) {
	            	                				EdgeRep er1 = (EdgeRep)it1.next();
	            	                				if (er1.desName.equalsIgnoreCase(er.desName)) {
	            	                					found = true;
	            	                					break;
	            	                				}
	            	                			}
	            	                			
	            	                			if (!found) {
		            	                			EdgeRep edgIncl = new EdgeRep("P",er.desName);
		            	                			dest.addLast(edgIncl);
		            	                			//update modifier boolean
	            	                			}
	            	                		}
            	            			}						
	        					}
	        					else if (path.equalsIgnoreCase("Field_c")) {
	        						
	        							//for ovs
	        							boolean srcSize = false;
	        						
            	            			//System.out.println("field name: " + varName + ":" + fldName);
            	            			LinkedList<EdgeRep> dest = varInfo.get(n.name().toString());
            	            			
            	            			//points to set of varName
            	            			LinkedList<EdgeRep> src = varInfo.get(varName);
            	            			if (src != null && dest != null) {
            	            				
            	            				if (src.size() > 1) {
            	            					srcSize = true; 
            	            				}
            	            				
	            	            			Iterator it = src.iterator();
	            	            			while (it.hasNext())
	            	                		{
	            	            				int forOvs = 0;
	            	            				String edgName = "";
	            	                			EdgeRep er = (EdgeRep)it.next();
	            	                			
	            	                			LinkedList<EdgeRep> src1 = varInfo.get(er.desName);
    	                						if (src1 != null) {
    	                							Iterator it1 = src1.iterator();
    	                							
    	                							while (it1.hasNext()) {
    	                								EdgeRep er1 = (EdgeRep)it1.next();
    	                								
    	                								if (er1.edgeType.equalsIgnoreCase("F") && (er1.fieldName.equalsIgnoreCase(fldName))) {
    	                									
    	                									Iterator it2 = dest.iterator();
    	                									boolean found = false;
    	                									while (it2.hasNext()) {
    	                										EdgeRep er2 = (EdgeRep)it2.next();
    	                										if(er1.desName.equalsIgnoreCase(er.desName) && er1.fieldName.equalsIgnoreCase(er.fieldName) ) {
    	                											found = true;
        	                										//for ovs
        	                										forOvs++;
        	                										edgName = er1.edgeName;
    	                											break;
    	                										}
    	                									}
    	                									if (!found) {
    	                										//for ovs
    	                										forOvs++;
    	                										edgName = er1.edgeName;
	    	                                					EdgeRep edgIncl = new EdgeRep("P",er1.desName);
	    	                                					dest.addLast(edgIncl);
	    	                                					
	    	                                					//update modifier boolean
    	                									}
    	                                				}
    	                							}
    	                						}
	            	                			
	            	                			HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
	            	                			if (setMethodInfo != null) {
	            	                				HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
	            	                				if (placeInfo != null) {
	            	                					HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
	            	                					if (setDetails != null) {
		            	                					HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
		            	                					HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
		            	                					HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
		            	               						if(!(er.desName.equalsIgnoreCase("Obj-null"))) {
		            	               							
		            	               							//for the gvs
		            	               							if (ovSet != null) {
		            	               								HashSet<String> ovs = ovSet.get("global-ovs");
		            	               								if (srcSize) {
		            	               									ovs.add(varName);
		            	               									ovs.add(n.name().toString());	
		            	               								}
		            	               								if (forOvs > 1) {
		            	               									ovs.add(edgName);
		            	               									ovs.add(n.name().toString());
		            	               									forOvs = 0;
		            	               								}
		            	               							}
		            	               							
		            	               							
		            	               							if(readSet != null && writeSet != null) {
		            	               								HashSet<String> ws = writeSet.get(er.desName);
		            	               								if ( ws != null && !(ws.contains(fldName))) {
			            	               								HashSet<String> rs = readSet.get(er.desName);
			            	               								if (rs != null) {
			            	               									//note sure whether contains works fine for HashSet
			            	               									if (!rs.contains(fldName)) {
			            	               										rs.add(fldName);
			            	               										//update modifier boolean
			            	               									}
			            	               								}
		            	               								}	
		            	               							}
		            	               						}
	            	                					}
	            	               					}
	            	               				}
    	                						
	            	                		}
            	            			}
	        					}
	        					else if (path.equalsIgnoreCase("NullLit_c")) {
	        						if(!(varInfo.containsKey(n.name().toString()))) {
	                            		varInfo.put(n.name().toString(), new LinkedList<EdgeRep>());
	                            		//update modifier boolean
	                            	}

	                            	LinkedList<EdgeRep> edgeIncl = varInfo.get(n.name().toString());
	                            	
	                            	if (edgeIncl != null) {
	                            		Iterator it = edgeIncl.iterator();
	                            		boolean found = false;
	                            		while (it.hasNext()) {
	                            			EdgeRep er = (EdgeRep)it.next();
	                            			
	                            			if (er.desName.equalsIgnoreCase("Obj-null")) {
	                            				found = true;
	                            				break;
	                            			}
	                            		}
	                            		if (!found) {
	                            			EdgeRep edgeInfo = new EdgeRep("P","Obj-null");
	                            			edgeIncl.addLast(edgeInfo);
	                            			//update modifier boolean
	                            		}
	                            	}
	        					}
	        				}
	        				
	        				//the back up after the changes in points to graph. NOTE:##! check to stop whether it can be done once alone
	        				varInfo = lineInfo.get(lineNo);
	        				if (varInfo != null) {
	        					lastGraphInfo.push(deepCopy(varInfo));
	        				}
	        			}
	        		}
	        	}
	        	else {
            		if(n.init() instanceof Field_c) {

            				Field_c tempField = (Field_c)n.init();
            				String fldName;
            				String varName;
            				String sprField = "";
            				boolean closureField = true;
            				
            				if(tempField.target() instanceof Field_c) {
    							fldName = tempField.name().toString();
    							Field_c tempField1 = (Field_c)tempField.target();
    							varName = tempField1.name().toString();
    						} 
            				else {
            					if (tempField.target() instanceof Special_c ) {
            						varName = "this";
            						Special_c specialFld = (Special_c)tempField.target();
            						if (specialFld.kind().toString().equalsIgnoreCase("super")) {
            							sprField = "spr.";
            						}
            						
            					}
            					else {
            						varName = tempField.target().toString();
            					}
            					 fldName = sprField+tempField.name().toString();
            				}

            				//System.out.println("field name: " + varName + ":"+ fldName);
                			HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
                			if (methodInfo != null && closureField) {
                				//System.out.println("HELLO I AM HERE");
                				HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
                				if (lineInfo != null) {
                					HashMap<String, LinkedList<EdgeRep>> varInfo = lastGraphInfo.peek();
	                				if (varInfo != null) {
	               						LinkedList<EdgeRep> src = varInfo.get(varName);
	               						
	               						if (src != null) {
	               							
	               	                	Iterator it = src.iterator();
	               	                	
		               	                	while (it.hasNext()) {
		               	                		EdgeRep er = (EdgeRep)it.next();
		               	                		
		               	                		
		               	                		HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
		               	               			if (setMethodInfo != null) {
		               	               				HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
		               	               				if (placeInfo != null) {
		               	               				HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
		                	               				if (setDetails != null) {
			                	            				HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
			                	                          	HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
			                	                          	HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
			                                           		if (readSet != null && writeSet != null) {
			               	                           			if(!(er.desName.equalsIgnoreCase("Obj-null"))) {
			               	                           				
			               	                           				//for the gvs
			            	               							if (ovSet != null) {
			            	               								HashSet<String> ovs = ovSet.get("global-ovs");
			            	               								if (src.size() > 1) {
			            	               									ovs.add(varName);
			            	               								}
			            	               							}
			               	                           				
			               	                           				
			               	                           				HashSet<String> ws = writeSet.get(er.desName);
			               	                           				if ( ws != null && !(ws.contains(fldName))) {
			               	                           					HashSet<String> rs = readSet.get(er.desName);
			               	                           					if (rs != null) {
				           	               									//note sure whether contains works fine for HashSet
				           	               									if (!rs.contains(fldName)) {
				           	               										rs.add(fldName);
				           	               										//update modifier boolean
				            	              								}
			                	                          				}
			                                           				}
			               	                           			}
			               	                           		}
			               	               				}
		               	               				}	
		               	               			}
		               	                	}
	               	                	}
	               					}
                				}
               				}     			
            		}
	        	}
	        }
	        
	        /* the printer code */
	        /*
        	VarWithLineNo temp1 = currClass.peek();
        	VarWithLineNo temp2 = null;
        	VarWithLineNo temp3 = null;
        	boolean goThrough = true;
        	//if ((temp1.name.equalsIgnoreCase("Histogram") || temp1.name.equalsIgnoreCase("test") || temp1.name.equalsIgnoreCase("$Closure$0"))) {
        		if (currMethod.size() > 0) {
        			temp2 = currMethod.peek();
        		} 
        		else {
        			goThrough = false;
        		}
        		if (currPlace.size() > 0) {
        		 temp3 = currPlace.peek();
        		}
        		else {
        			goThrough = false;
        		}
        	//}
        	//System.out.println("I AM PRINTING: " + temp1.lineNo +":"+ temp2.lineNo +":"+ temp3.lineNo +":" + lineNo);
        	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
        	if (methodInfo != null && goThrough) {
        		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
        		if (lineInfo != null) {
        			HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
        			if (varInfo != null) {
        				System.out.println("GRAPH AFTER local dec: " + lineNo);
        				Iterator it = varInfo.entrySet().iterator();
        	        	while (it.hasNext()) {
        	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
        	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
        	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
        	        		Iterator it1 = edgeIncl.iterator();
        	        		while (it1.hasNext())
        	        		{
        	        			EdgeRep er = (EdgeRep)it1.next();
        	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
        	        					+ er.fieldName);
        	        		}
        	        	}
        			}
        		}
        	}
        	
        	//the sets
        	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
        	if (setMethodInfo != null &&  goThrough) {
        		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
        		if (placeInfo != null) {
        			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
        			if (setDetails != null) {
        				System.out.println();
    		    		//System.out.println("Method's Object Sets:" + temp1.lineNo + ":" +temp2.lineNo +":" + temp3.lineNo);
    		    		
    		    		System.out.println("Read Set:");
    		    		HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
    		    		if (readSet != null) {
    		    			Iterator it3 = readSet.entrySet().iterator();
    		    			while (it3.hasNext()) {
    		    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it3.next();
    		        			System.out.println("Object: " + pair.getKey());
    		        			HashSet<String> rs = (HashSet<String>)pair.getValue();
    		        			
    		        			if(rs != null) {
    		        				Iterator it4 = rs.iterator();
    		        				
    		        				while (it4.hasNext()) {
    		            				String str = (String)it4.next();
    		            				System.out.print(" {" + str +"} ");
    		            			}
    		            			System.out.println("");
    		        			}
    		    			}
    		    		}
    		    		
    		    		System.out.println("");
			    		System.out.println("OV Set:");
			    		HashMap<String, HashSet<String>> ovSet = setDetails.get("OVS");
			    		if (ovSet != null) {
			    			Iterator it3 = ovSet.entrySet().iterator();
			    			while (it3.hasNext()) {
			    				Map.Entry<String, HashSet<String>> pair = (Map.Entry<String, HashSet<String>>)it3.next();
			        			System.out.println("Object: " + pair.getKey());
			        			HashSet<String> ovs = (HashSet<String>)pair.getValue();
			        			
			        			if(ovs != null) {
			        				Iterator it4 = ovs.iterator();
			        				
			        				while (it4.hasNext()) {
			            				String str = (String)it4.next();
			            				System.out.print(" {" + str +"} ");
			            			}
			            			System.out.println("");
			        			}
			    			}
			    		}
        			}
        		}
        	}
        	
        	System.out.println("--------------------------------------------------end of local dec--------------------------------------");
        	*/
        	/* the printer code */
	        
	        
        	
	        /* Nobita code */
            
            if (isMutableStruct(n.type().type())) {
                w.write(".clone()");
            }

        }
        // assign default value for access vars in at or async
        else if (!n.flags().flags().isFinal()) {
            Type type = Types.baseType(n.type().type());
            TypeSystem xts = tr.typeSystem();

            w.write(" = ");

            if (xts.isBoolean(type)) {
                w.write(" false");
            } else if (!xts.isParameterType(type) &&
                    (xts.isChar(type) || xts.isNumeric(type))) {
                w.write(" 0");
            } else {
                w.write(" null");
            }
        }

        if (printSemi) {
            w.write(";");
        }

        tr.printType(printType);
        tr.appendSemicolon(printSemi);
    }

    @Override
    public void visit(LocalTypeDef_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(Loop_c n) {
    	/* Nobita code */
    	//in
    	
    	lineNo++;
    	int lineNoBeforeLoop = lineNo;
    	if ((currClass.size() > 0) && (currMethod.size() > 0)) {
	    	//getting the class,the pointer and the method
	    	VarWithLineNo temp1 = currClass.peek();
	    	VarWithLineNo temp2 = currMethod.peek();
	    	VarWithLineNo temp3 = currPlace.peek();
	    	
	    	
	    	//the graph
	    	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
	    	if (methodInfo != null) {
	    		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
	    		if (lineInfo != null) {
	    			//the old updated ponts to info
	    			HashMap<String, LinkedList<EdgeRep>> z1 = null;
					if (lastGraphInfo.size() > 0) {
						if (lineInfo.containsKey(lineNo)) {
							z1 = lineInfo.get(lineNo);
						}
						
						if (iterationCount <= 0) {
							lineInfo.put(lineNo, lastGraphInfo.peek());
						}
					}
					
					HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
					if (varInfo != null) {
	    				HashMap<String, LinkedList<EdgeRep>> src = deepCopy(varInfo);
	    				if (src != null) {
	    					theLoop.add(src);
	    					if (z1 != null) {
	    						lastGraphInfo.pop();
	    						lastGraphInfo.push(z1);
	    					}
	    				}
	    			}
	    		}
	    	}
	    	
	    	//the sets
	    	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
	    	if (setMethodInfo != null) {
	    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
	    		if (placeInfo != null) {
	    			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
	    			if (setDetails != null) {
	    				HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
	    				if (writeSet != null) {
	    					HashMap<String, HashSet<String>> srcSet =  deepCopySet(writeSet);
	    					if(srcSet != null) {
	    						theLoopSet.add(srcSet);
	    					}
	    				}
	    			}
	    		}
	    	}
    	
    	}
    	/* Nobita code */
    	
        n.translate(w, tr);
        
        /* Nobita code */
    	lineNo++;
    	if ((currClass.size() > 0) && (currMethod.size() > 0)) {
	    	//getting the class,the pointer and the method
	    	VarWithLineNo temp1 = currClass.peek();
	    	VarWithLineNo temp2 = currMethod.peek();
	    	VarWithLineNo temp3 = currPlace.peek();
	    	
	    	//the graph
	    	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
	    	if (methodInfo != null) {
	    		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
	    		if (lineInfo != null) {
					if (lastGraphInfo.size() > 0) {
						lineInfo.put(lineNo, lastGraphInfo.pop());
					}
					
					HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
					if (varInfo != null) {
						HashMap<String, LinkedList<EdgeRep>> mergeBack = payBack (varInfo);
						if (mergeBack != null) {
							//triggers the next iteration
							///////////////////////the printer////////////////////////////
							/* HashMap<String, LinkedList<EdgeRep>> varInfo1 = lineInfo.get(lineNoBeforeLoop);
							System.out.println("DISPLAYING lineNoBeforeLoop: " + lineNoBeforeLoop);
		    				Iterator it = varInfo1.entrySet().iterator();
		    	        	while (it.hasNext()) {
		    	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
		    	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
		    	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
		    	        		Iterator it1 = edgeIncl.iterator();
		    	        		while (it1.hasNext())
		    	        		{
		    	        			EdgeRep er = (EdgeRep)it1.next();
		    	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
		    	        					+ er.fieldName);
		    	        		}
		    	        	}
		    	        	
		    	        	varInfo1 = mergeBack;
							System.out.println("DISPLAYING MERGEBACK: " + lineNo);
		    				it = varInfo1.entrySet().iterator();
		    	        	while (it.hasNext()) {
		    	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
		    	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
		    	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
		    	        		Iterator it1 = edgeIncl.iterator();
		    	        		while (it1.hasNext())
		    	        		{
		    	        			EdgeRep er = (EdgeRep)it1.next();
		    	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
		    	        					+ er.fieldName);
		    	        		}
		    	        	} */
							///////////////////////////the printer////////////////////////
							if (!nextIteration) {
								nextIteration = payBackBoolean(lineInfo.get(lineNoBeforeLoop), mergeBack);
								//System.out.println("INSIDE THE LOOP T/F: " + nextIteration + ":" + payBackBoolean(lineInfo.get(lineNoBeforeLoop), mergeBack));
							}
							lineInfo.put(lineNo, mergeBack);
							lineInfo.put(lineNoBeforeLoop, mergeBack);
							lastGraphInfo.push(mergeBack);
						}
	    			}
	    		}
	    	}
	    	
	    	//the sets
	        payBackSet();
    	}
    	
    	/* the printer code */
    	/*
    	VarWithLineNo temp1 = currClass.peek();
    	VarWithLineNo temp2 = null;
    	VarWithLineNo temp3 = null;
    	boolean goThrough = true;
    	//if ((temp1.name.equalsIgnoreCase("Histogram") || temp1.name.equalsIgnoreCase("test"))) {
    		if (currMethod.size() > 0) {
    			temp2 = currMethod.peek();
    		} 
    		else {
    			goThrough = false;
    		}
    		if (currPlace.size() > 0) {
    		 temp3 = currPlace.peek();
    		}
    		else {
    			goThrough = false;
    		}
    	//}
    	System.out.println("The next iteration value: " + nextIteration);
    	//System.out.println("I AM PRINTING: " + temp1.lineNo +":"+ temp2.lineNo +":"+ temp3.lineNo +":" + lineNo);
    	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo = graphInfo.get(temp1.lineNo);
    	if (methodInfo != null && goThrough) {
    		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo.get(temp2.lineNo);
    		if (lineInfo != null) {
    			HashMap<String, LinkedList<EdgeRep>> varInfo = lineInfo.get(lineNo);
    			if (varInfo != null) {
    				System.out.println("GRAPH AFTER LOOP CONDITION: " + lineNo);
    				Iterator it = varInfo.entrySet().iterator();
    	        	while (it.hasNext()) {
    	        		Map.Entry<String, LinkedList<EdgeRep>> pair = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
    	        		System.out.println("The variable: " + pair.getKey() + " points to: ");
    	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)pair.getValue());
    	        		Iterator it1 = edgeIncl.iterator();
    	        		while (it1.hasNext())
    	        		{
    	        			EdgeRep er = (EdgeRep)it1.next();
    	        			System.out.println("Object: " + er.desName + "  COPY-FLAG: " + er.copyFlag + " EDGE-TYPE: " + er.edgeType + " FIELD-NAME: "
    	        					+ er.fieldName + " EdgeName: " + er.edgeName);
    	        		}
    	        	}
    			}
    		}
    		System.out.println("--------------------------------------------------end of loop cond--------------------------------------");
    	}
    	*/
    	/* the printer code */
    	/* Nobita code */
    }

    @Override
    public void visit(Return_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(Switch_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(Throw_c n) {
        n.translate(w, tr);
    }

    @Override
    public void visit(Try_c c) {
        TryCatchExpander expander = new TryCatchExpander(w, er, c.tryBlock(), c.finallyBlock());
        final List<Catch> catchBlocks = c.catchBlocks();

        boolean isConstrainedThrowableCaught = false; // XTENLANG-2384
        for (int i = 0; i < catchBlocks.size(); ++i) {
            Type type = catchBlocks.get(i).catchType();
            if (type instanceof ConstrainedType) // XTENLANG-2384: Check if there is a constained type in catchBlocks
                isConstrainedThrowableCaught = true;
        }

        // XTENLANG-2384: If there is a constrained type, generate if sequence instead of catch sequence
        if (isConstrainedThrowableCaught) {
            final String temp = "$ex";
            int convRequired = 0;
            expander.addCatchBlock(JAVA_LANG_THROWABLE, temp, new Expander(er) {
                public void expand(Translator tr) {
                    w.newline();
                    for (int i = 0; i < catchBlocks.size(); ++i) {
                        Catch cb = catchBlocks.get(i);
                        Type type = cb.catchType();
                        w.write("if (" + temp + " instanceof ");
                        er.printType(type, 0);
                        if (type instanceof ConstrainedType) {
                            ConstrainedType ctype = (ConstrainedType)type;
                            //w.write(" && true/* Constraint condition check */"); // TODO: add constraint check here
                        }
                        w.write(")"); w.newline();
                        cb.body().translate(w, tr);
                        w.write("else "); w.newline();
                    }
                    // should not come here
                    w.write("{ "+ temp + ".printStackTrace(); assert false; }"); w.newline();
                }
            });
        } else { // XTENLANG-2384: Normal case, no constrained type in catchBlocks
            final String temp = "$ex";
            for (int i = 0; i < catchBlocks.size(); ++i) {
                Catch catchBlock = catchBlocks.get(i);
                expander.addCatchBlock(catchBlock);
            }
        }
        expander.expand(tr);
    }

    // ////////////////////////////////
    // end of Stmt
    // ////////////////////////////////

    @Override
    public void visit(CanonicalTypeNode_c n) {
        Type t = n.type();
        if (t != null)
            er.printType(t, PRINT_TYPE_PARAMS);
        else
            // WARNING: it's important to delegate to the appropriate visit()
            // here!
            visit((Node) n);
    }

    @Override
    public void visit(TypeDecl_c n) {
        // Do not write anything.
        return;
    }

    @Override
    public void visit(Id_c n) {
        w.write(Emitter.mangleToJava(n.id()));
    }

    public static boolean isString(Type type) {
        return Types.baseType(type).isString();
    }
    
    public static boolean isRail(Type type) {
        return Types.baseType(type).isRail();
    }


    // TODO consolidate isPrimitive(Type) and needExplicitBoxing(Type).
    public static boolean isPrimitive(Type t) {
        return t.isBoolean() || t.isChar()  || t.isNumeric();
    }

    public static boolean isSpecialType(Type type) {
        return isPrimitive(Types.baseType(type));
    }
    
    /**
     * Returns true if the method does not satisfy the boxing rules. 
     * The currently existing example is Java array access methods, which are declared as generic methods,
     * but are implemented without boxing using @Native snippets.
     * @param mi - MethodInstance
     * @return true if method should be treated specially wrt argument and return value boxing.
     */
    public static boolean isPrimitiveGenericMethod(MethodInstance mi) {
        QName fullName = mi.container().fullName();
        if (fullName.toString().equals("x10.interop.Java.array")) return true;
        if (exposeSpecialDispatcherThroughSpecialInterface) {
            Name name = mi.name();
            List<LocalInstance> formalNames = mi.formalNames();
            if (fullName.equals(Emitter.X10_LANG_ARITHMETIC)) {
                // dispatch method ($G->$I etc.)
                if (formalNames != null && formalNames.size() == 1 &&
                    (OperatorNames.PLUS.equals(name) || OperatorNames.MINUS.equals(name) || OperatorNames.STAR.equals(name) || OperatorNames.SLASH.equals(name)))
                    return true;
            }
            else if (fullName.equals(Emitter.X10_LANG_BITWISE)) {
                // dispatch method ($G->$I etc.)
                if (formalNames != null && formalNames.size() == 1 &&
                    (OperatorNames.AMPERSAND.equals(name) || OperatorNames.BAR.equals(name) || OperatorNames.CARET.equals(name)))
                    return true;
            }
            else if (fullName.equals(Emitter.X10_LANG_REDUCIBLE)) {
                // dispatch method ($G->$I etc.)
                if (formalNames != null && formalNames.size() == 2 &&
                    (OperatorNames.APPLY.equals(name)))
                    return true;
            }
            else if (fullName.equals(Emitter.X10_LANG_ITERATOR)) {
                // special return type ($G->$O)
                if ((formalNames == null || formalNames.size() == 0) &&
                    ("next".equals(name.toString())))
                    return true;
            }
            else if (fullName.equals(Emitter.X10_LANG_SEQUENCE)) {
                // special return type ($G->$O)
                if (formalNames != null && formalNames.size() == 1 &&
                    (OperatorNames.APPLY.equals(name)))
                    return true;
            }
        }
        return false;
    }

    public static boolean isSpecialTypeForDispatcher(Type type) {
        // XTENLANG-2993
        return isSpecialType(type) || type.isVoid();
    }

    public static boolean hasParams(Type t) {
        Type bt = Types.baseType(t);
        TypeSystem ts = bt.typeSystem();
        return (bt.isClass() && !ts.isJavaArray(bt) && bt.toClass().hasParams());
    }

    public static boolean containsTypeParam(List<Ref<? extends Type>> list) {
        for (Ref<? extends Type> ref : list) {
            if (Emitter.containsTypeParam(ref.get())) {
                return true;
            }
        }
        return false;
    }

    private final static class ConstructorIdTypeForAnnotation extends X10ParsedClassType_c {
        private static final long serialVersionUID = 1L;
        private int i = -1;
        private ConstructorIdTypeForAnnotation(X10ClassDef def) {
            super(def);
        }
        private ConstructorIdTypeForAnnotation setIndex(int i) {
            assert i > -1;
            this.i = i;
            return this;
        }
        private int getIndex() {
            return i;
        }
    }
    
	/* Nobita - new methods */
    //the graph
    /* Nobita code - a method */
    public HashMap<String, LinkedList<EdgeRep>> deepCopy (HashMap<String, LinkedList<EdgeRep>> src) {
    	HashMap<String, LinkedList<EdgeRep>> copyGraphInfo = new HashMap<String, LinkedList<EdgeRep>>();
    	if (src != null) {
    		//the copier
        	Iterator it = src.entrySet().iterator();
        	
        	while (it.hasNext()) {
        		Map.Entry<String, LinkedList<EdgeRep>> phase3 = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)phase3.getValue());
        		
        		if(edgeIncl != null) {
        			Iterator it1 = edgeIncl.iterator();
        			
        			//inserting into new DS
        			copyGraphInfo.put(phase3.getKey(), new LinkedList<EdgeRep>());
            		LinkedList<EdgeRep> edgeCopy = copyGraphInfo.get(phase3.getKey());
        			
        			while (it1.hasNext()) {
        				EdgeRep er = (EdgeRep)it1.next();
            			EdgeRep edgeDest = new EdgeRep(er.edgeType, er.desName, er.fieldName, er.copyFlag);
            			edgeDest.edgeName = er.edgeName;
            			edgeCopy.addLast(edgeDest);
        			}
        		}
        	}
    	}
    	
    	return copyGraphInfo;
    }
    
    /* Nobita code - a method */
    public HashMap<String, LinkedList<EdgeRep>> payBack (HashMap<String, LinkedList<EdgeRep>> src) {
    	if (src != null) {
    		
    		//for assigning the edge name
    		HashMap<String, HashMap<String, String>> edgeNameAssign = new HashMap<String, HashMap<String, String>>();
    		
	    	if(theLoop.size() > 0) {
	    		HashMap<String, LinkedList<EdgeRep>> payGraphInfo = theLoop.pop();
	    		
	    		Iterator it = payGraphInfo.entrySet().iterator();
	    		
	    		while (it.hasNext()) {
	    			
	    			Map.Entry<String, LinkedList<EdgeRep>> phase3 = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)phase3.getValue());
	        		
	        		if (edgeIncl != null) {
	        			Iterator it1 = edgeIncl.iterator();
	        			
	        			//replacing started
	            		LinkedList<EdgeRep> src1 = src.get(phase3.getKey());
	            		
	            		while (it1.hasNext()) {
	            			EdgeRep er = (EdgeRep)it1.next();
	            			
	            			if (src1 != null) {
	            				Iterator it2 = src1.iterator();
	                			boolean found = false;
	                			
	                			while (it2.hasNext()) {
	                				EdgeRep er1 = (EdgeRep)it2.next();
	                				if((er.desName.equalsIgnoreCase(er1.desName))  && (er.fieldName.equalsIgnoreCase(er1.fieldName))) {
	                					found = true;
	                				}
	                				
	                				if(found) {
	                					break;
	                				}
	                			}
	                			
	                			if(!found) {
	                				//giving new name to multiple field edges
	                				if (!er.fieldName.equalsIgnoreCase("")) {
	                					if (!edgeNameAssign.containsKey(phase3.getKey())) {
	                						edgeNameAssign.put(phase3.getKey(), new HashMap<String, String>());
	                					}
	                					HashMap<String, String> en = edgeNameAssign.get(phase3.getKey());
	                					if (!en.containsKey(er.fieldName)) {
	                						en.put(er.fieldName, ("zx"+edgeNumber));
	                						edgeNumber++;	
	                					}
	                				}
	                				src1.addLast(er);
	                			}
	            			}
	            		}
	        		}
	    			
	    		}
	    	}
	    	
	    	//for assigning the edge name
	    	Iterator it = edgeNameAssign.entrySet().iterator();
	    	while (it.hasNext()) {
	    		Map.Entry<String, HashMap<String, String>> phase2 = (Map.Entry<String, HashMap<String, String>>)it.next();
	    		HashMap<String, String> fieldIncl = ((HashMap<String, String>)phase2.getValue());
	    		
	    		if (fieldIncl != null) {
	    			Iterator it1 = fieldIncl.entrySet().iterator();
	    			while (it1.hasNext()) {
	    				Map.Entry<String, String> phase3 = (Map.Entry<String, String>)it1.next();
	    				
	    				LinkedList<EdgeRep> objName = src.get(phase2.getKey());
	    				Iterator it2 = objName.iterator();
	    				while (it2.hasNext()) {
	    					EdgeRep er = (EdgeRep)it2.next();	
	    					if (er.fieldName.equalsIgnoreCase(phase3.getKey())) {
	    						er.edgeName = phase3.getValue();
	    					}
	    				}
	    				
	    			}
	    		}
	    	}
	    	
	    	//for assigning the edge name
    	}
    	return src;
    }
    
    /* Nobita code - a method */
    public HashMap<String, LinkedList<EdgeRep>> payBack1 (HashMap<String, LinkedList<EdgeRep>> src, HashMap<String, LinkedList<EdgeRep>> merger2 ) {
    	if (src != null && merger2 != null) {
    		
    		//for assigning the edge name
    		HashMap<String, HashMap<String, String>> edgeNameAssign = new HashMap<String, HashMap<String, String>>();
    		
    		HashMap<String, LinkedList<EdgeRep>> payGraphInfo = merger2;
    		
    		//the iterator
        	Iterator it = payGraphInfo.entrySet().iterator();
        	
        	while (it.hasNext()) {
        		Map.Entry<String, LinkedList<EdgeRep>> phase3 = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)phase3.getValue());
        		
        		if (edgeIncl != null) {
        			Iterator it1 = edgeIncl.iterator();
        			//replacing started
            		LinkedList<EdgeRep> src1 = src.get(phase3.getKey());
            		
            		while (it1.hasNext()) {
            			EdgeRep er = (EdgeRep)it1.next();
            			
            			if (src1 != null) {
            				Iterator it2 = src1.iterator();
                			boolean found = false;
                			while (it2.hasNext()) {
                				EdgeRep er1 = (EdgeRep)it2.next();
                				if(er.desName.equalsIgnoreCase(er1.desName) && er.fieldName.equalsIgnoreCase(er1.fieldName)) {
                					found = true;
                				}
                				
                				if(found) {
                					break;
                				}
                			}
                			
                			if(!found) {
                				//giving new name to multiple field edges
                				if (!er.fieldName.equalsIgnoreCase("")) {
                					if (!edgeNameAssign.containsKey(phase3.getKey())) {
                						edgeNameAssign.put(phase3.getKey(), new HashMap<String, String>());
                					}
                					HashMap<String, String> en = edgeNameAssign.get(phase3.getKey());
                					if (!en.containsKey(er.fieldName)) {
                						en.put(er.fieldName, ("zx"+edgeNumber));
                						edgeNumber++;	
                					}
                				}
                				src1.addLast(er);
                			}
            			}
            		}
        			
        		}
        	}
        	
        	//for assigning the edge name
	    	Iterator it4 = edgeNameAssign.entrySet().iterator();
	    	while (it4.hasNext()) {
	    		Map.Entry<String, HashMap<String, String>> phase2 = (Map.Entry<String, HashMap<String, String>>)it4.next();
	    		HashMap<String, String> fieldIncl = ((HashMap<String, String>)phase2.getValue());
	    		
	    		if (fieldIncl != null) {
	    			Iterator it1 = fieldIncl.entrySet().iterator();
	    			while (it1.hasNext()) {
	    				Map.Entry<String, String> phase3 = (Map.Entry<String, String>)it1.next();
	    				
	    				LinkedList<EdgeRep> objName = src.get(phase2.getKey());
	    				Iterator it2 = objName.iterator();
	    				while (it2.hasNext()) {
	    					EdgeRep er = (EdgeRep)it2.next();	
	    					if (er.fieldName.equalsIgnoreCase(phase3.getKey())) {
	    						er.edgeName = phase3.getValue();
	    					}
	    				}
	    				
	    			}
	    		}
	    	}
	    	
	    	//for assigning the edge name
    	}
    	return src;
    }
    
    //the sets
    /* Nobita code - a method */
    public HashMap<String, HashSet<String>> deepCopySet (HashMap<String, HashSet<String>> src) {
    	HashMap<String, HashSet<String>> copySetInfo = new HashMap<String, HashSet<String>>();
    	
    	if (src != null) {
    		//the copier 
    		Iterator it = src.entrySet().iterator();
        	
    		while (it.hasNext()) {
    			Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it.next();
        		HashSet<String> setIncl = (HashSet<String>)phase3.getValue();
        		
        		if (setIncl != null) {
        			Iterator it1 = setIncl.iterator();
        			
        			//inserting into new DS
        			copySetInfo.put(phase3.getKey(), new HashSet<String>());
            		HashSet<String> setCopy = copySetInfo.get(phase3.getKey());
            		
            		while (it1.hasNext()) {
            			String str = (String)it1.next();
            			setCopy.add(str);
            		}
        		}
    		}
    	}
    	return copySetInfo;
    }
    
    /* Nobita code - a method */
    public void payBackSet () {
    	VarWithLineNo temp1 = currClass.peek();
    	VarWithLineNo temp2 = currMethod.peek();
    	VarWithLineNo temp3 = currPlace.peek();
    	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
    	if (setMethodInfo != null) {
    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
    		if (placeInfo != null) {
    			HashMap<String, HashMap<String, HashSet<String>>> setDetails = placeInfo.get(temp3.lineNo);
    			if (setMethodInfo != null) {
    				HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
					HashMap<String, HashSet<String>> mWriteSet = setDetails.get("MWS");
					
					if (theLoopSet.size() > 0 && writeSet != null && mWriteSet!= null ) {
						HashMap<String, HashSet<String>> paySetInfo = theLoopSet.pop();
						
						Iterator it = paySetInfo.entrySet().iterator();
						while (it.hasNext()) {
							Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it.next();
							
							HashSet<String> set1 = (HashSet<String>)phase3.getValue();
							HashSet<String> set2 = writeSet.get(phase3.getKey());
							HashSet<String> set3 = mWriteSet.get(phase3.getKey());
							
							if(set1 != null && set2 != null && set3 != null) {
								set1.retainAll(set2);
								writeSet.replace(phase3.getKey(), set1);
								
								set2.removeAll(set1);
								set3.addAll(set2);
							}
							
						}
					}
    			}
    		}
    	}
    }
    
    
    /* Nobita code - a method */
    public HashMap<String, HashMap<String, HashSet<String>>> deepCopySetIf (HashMap<String, HashMap<String, HashSet<String>>> src) {
    	HashMap<String, HashMap<String, HashSet<String>>> setDetails = new HashMap<String, HashMap<String, HashSet<String>>>();
    	
    	if (src != null) {
    		//the iterator
        	Iterator it = src.entrySet().iterator();
        	
        	while (it.hasNext()) {
        		Map.Entry<String, HashMap<String, HashSet<String>>> phase2 = (Map.Entry<String, HashMap<String,HashSet<String>>>) it.next();

        		//inserting sets into new [setDetails]
        		setDetails.put(phase2.getKey(), new HashMap<String, HashSet<String>>());
        		HashMap<String, HashSet<String>> sets  = (HashMap<String, HashSet<String>>)phase2.getValue();
        		
        		if (sets != null) {
        			//the variable insertion-1
            		HashMap<String, HashSet<String>> variable = setDetails.get(phase2.getKey());
            		
            		Iterator it1 = sets.entrySet().iterator();
            		while (it1.hasNext()) {
            			Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it1.next();
            			
            			//the variable insertion-2
            			variable.put(phase3.getKey(), new HashSet<String>());
            			HashSet<String> values = (HashSet<String>)phase3.getValue();
            			
            			if (values != null) {
            				Iterator it2 = values.iterator();
            				
            				//the value insertion-1
                			HashSet<String> value = variable.get(phase3.getKey());
                			
                			while (it2.hasNext()) {
                				String str  = (String)it2.next();
                				value.add(str);
                			}
            				
            			}
            		}
        		}
        	}
    	}
    	return setDetails;
    }
    
    /* Nobita code - a method */
    public HashMap<String, HashMap<String, HashSet<String>>> payBackSet1 (HashMap<String, HashMap<String, HashSet<String>>> merger1, 
    		HashMap<String, HashMap<String, HashSet<String>>> merger2, boolean alter) {
    	if (merger1 != null && merger2 != null) {
    		HashMap<String, HashMap<String, HashSet<String>>> paySetInfo = merger2;
    		
    		//the iterator
        	Iterator it = paySetInfo.entrySet().iterator();
        	
        	while (it.hasNext()) {
        		Map.Entry<String, HashMap<String, HashSet<String>>> phase2 = (Map.Entry<String, HashMap<String,HashSet<String>>>) it.next();
        		if((phase2.getKey()).equalsIgnoreCase("WS")) {
        			HashMap<String, HashSet<String>> sets  = (HashMap<String, HashSet<String>>)phase2.getValue();
        			HashMap<String, HashSet<String>> setsMerger1 = merger1.get(phase2.getKey());
        			
        			if (sets != null && setsMerger1 != null) {
        				Iterator it1 = sets.entrySet().iterator();
        				
        				while (it1.hasNext()) {
        					Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it1.next();
        					
        					HashSet<String> set3 = null;
        					HashMap<String, HashSet<String>> forSet3 = paySetInfo.get("MWS");
        					if (forSet3 != null) {
        						set3 = forSet3.get(phase3.getKey());
        					}
                			
                			HashSet<String> set1 = (HashSet<String>)phase3.getValue();
                			HashSet<String> set2 = setsMerger1.get(phase3.getKey());
                			HashSet<String> set4 = new HashSet<String>();
                			
                			if (set1 != null && set2 != null && set3 != null) {
                				Iterator it2 = set1.iterator();
                    			
                    			if (alter) {
            	        			while (it2.hasNext()) {
            	        				String str  = (String)it2.next();
            	        				set4.add(str);
            	        			}
                    			}
                    			
                    			//intersection
                    			set1.retainAll(set2);
                    			
                    			//difference of set2 to WMWS
                    			set2.removeAll(set1);
                    			set3.addAll(set2);
                    			
                    			if (alter) {
            	        			//difference of set1 to WMS
            	        			set4.removeAll(set1);
            	        			set3.addAll(set4);
                    			}
                			}
        				}
        			}
        		}
        		else {
        			HashMap<String, HashSet<String>> sets  = (HashMap<String, HashSet<String>>)phase2.getValue();
        			HashMap<String, HashSet<String>> setsMerger1 = merger1.get(phase2.getKey());
        			
        			if(sets != null && setsMerger1 != null) {
        				Iterator it1 = sets.entrySet().iterator();
        				while (it1.hasNext()) {
        					Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it1.next();
                			
                			HashSet<String> set1 = (HashSet<String>)phase3.getValue();
                			HashSet<String> set2 = setsMerger1.get(phase3.getKey());
                			
                			if (set1 != null && set2 != null) {
                				set1.addAll(set2);
                			}
        				}
        			}
        		}
        	}

        	
        	//write back the other side merging code
        	HashMap<String, HashSet<String>> m1RSet = merger1.get("RS");
        	if (m1RSet != null) {
        		Iterator it5 = m1RSet.entrySet().iterator();
        		
        		while (it5.hasNext()) {
        			Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it5.next();
        			
        			HashMap<String, HashSet<String>> m2RSet = merger2.get("RS");
        			if (!m2RSet.containsKey(phase3.getKey())) {
        				
        				//read
        				m2RSet.put(phase3.getKey(), new HashSet<String>());
        				HashSet<String> m2RS = m2RSet.get(phase3.getKey());
        				HashSet<String> set1 = m1RSet.get(phase3.getKey());
        				m2RS.addAll(set1);
        				
        				//cumulative read
        				HashMap<String, HashSet<String>> m2RCSet = merger2.get("CRS");
        				m2RCSet.put(phase3.getKey(), new HashSet<String>());
        				HashSet<String> m2CRS = m2RCSet.get(phase3.getKey());
        				
        				HashMap<String, HashSet<String>> m1RCSet = merger1.get("CRS");
        				HashSet<String> set2 = m1RCSet.get(phase3.getKey());
        				m2CRS.addAll(set2);
        				
        				//may write read
        				HashMap<String, HashSet<String>> m2MWSet = merger2.get("MWS");
        				m2MWSet.put(phase3.getKey(), new HashSet<String>());
        				HashSet<String> m2MWS = m2MWSet.get(phase3.getKey());
        				
        				HashMap<String, HashSet<String>> m1MWSet = merger1.get("MWS");
        				HashSet<String> set3 = m1MWSet.get(phase3.getKey());
        				m2MWS.addAll(set3);
        				
        				
        				//Write read
        				HashMap<String, HashSet<String>> m2WSet = merger2.get("WS");
        				m2WSet.put(phase3.getKey(), new HashSet<String>());
        				
        				HashMap<String, HashSet<String>> m1WSet = merger1.get("WS");
        				HashSet<String> set4 = m1WSet.get(phase3.getKey());
        				m2MWS.addAll(set4);
        				
        				//cumulative Write set
        				HashMap<String, HashSet<String>> m2CWSet = merger2.get("CWS");
        				m2CWSet.put(phase3.getKey(), new HashSet<String>());
        				HashSet<String> m2CWS = m2CWSet.get(phase3.getKey());
        				
        				HashMap<String, HashSet<String>> m1CWSet = merger1.get("CWS");
        				HashSet<String> set5 = m1CWSet.get(phase3.getKey());
        				m2CWS.addAll(set5);
        				
        				//code for OS
        				//to do
        				
        			}
        			
        		}
        	}
        	
        	return paySetInfo;
    	}
    	return null;
    }
    
    /* Nobita code - a method */
    //the object
    public HashMap<String, ObjNode> deepCopyObject (HashMap<String, ObjNode> src) {
    	HashMap<String, ObjNode> copyObjectInfo = new HashMap<String, ObjNode>();
    	
    	if (src != null) {
    		//the copier 
    		Iterator it = src.entrySet().iterator();
    		
    		while (it.hasNext()) {
    			Map.Entry<String, ObjNode> phase3 = (Map.Entry<String, ObjNode>)it.next();
    			ObjNode objIncl = ((ObjNode)phase3.getValue());
    			
    			if (objIncl != null) {
    				ObjNode on = new ObjNode(objIncl.name, objIncl.objType, objIncl.copyFlag);
    				copyObjectInfo.put(phase3.getKey(), on);
    			}
    		}
    	}
    	
    	return copyObjectInfo;
    }
    
    /* Nobita code - a method */
    //the place - for the union of write sets into cumulative and others to blank
    public HashMap<String, HashMap<String, HashSet<String>>> copySetToNewPlace(HashMap<String, HashMap<String, HashSet<String>>> src) {
    	HashMap<String, HashMap<String, HashSet<String>>> copySetInfo = new HashMap<String, HashMap<String, HashSet<String>>>();
    	copySetInfo.put("CWS", new HashMap<String, HashSet<String>>());
    	
    	if (src != null) {
    		Iterator it = src.entrySet().iterator();
    		
    		while (it.hasNext()) {
    			Map.Entry<String, HashMap<String, HashSet<String>>> phase2 = (Map.Entry<String, HashMap<String, HashSet<String>>>)it.next();
    			HashMap<String, HashSet<String>> setname = (HashMap<String, HashSet<String>>)phase2.getValue();
    			
    			if(phase2.getKey().equalsIgnoreCase("WS") || phase2.getKey().equalsIgnoreCase("MWS") || phase2.getKey().equalsIgnoreCase("CWS")) {
    				if (phase2.getKey().equalsIgnoreCase("WS") || phase2.getKey().equalsIgnoreCase("MWS")) {
    					//WS and MWS
    					copySetInfo.put(phase2.getKey(), new HashMap<String, HashSet<String>>());
    					HashMap<String, HashSet<String>> objSet = copySetInfo.get(phase2.getKey());
    					
    					Iterator it1 = setname.entrySet().iterator();
        				while (it1.hasNext()) {
        					Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it1.next();
        					objSet.put(phase3.getKey(), new HashSet<String>());
        					
        					HashMap<String, HashSet<String>> cwSet = copySetInfo.get("CWS");
        					if(!cwSet.containsKey(phase3.getKey())) {
        						cwSet.put(phase3.getKey(), new HashSet<String>());
        					}
        					
        					HashSet<String> set1 = cwSet.get(phase3.getKey());
        					HashSet<String> set2 = (HashSet<String>)phase3.getValue();
        					if (set1 != null && set2 != null) {
        						set1.addAll(set2);
        					}
        					
        				}
    					
    				}
    				else {
    					HashMap<String, HashSet<String>> objSet = copySetInfo.get(phase2.getKey());
    					
    					Iterator it1 = setname.entrySet().iterator();
    					while (it1.hasNext()) { 
    						Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it1.next();
    						
    						if (!objSet.containsKey(phase3.getKey())) {
    							objSet.put(phase3.getKey(), new HashSet<String>());
    						}
    						
    						HashSet<String> set1 = objSet.get(phase3.getKey());
    						HashSet<String> set2 = (HashSet<String>)phase3.getValue();
    						
    						if (set1 != null && set2 != null) {
        						set1.addAll(set2);
        					}
    						
    					}
    				}
    			}
    			else {
    				//for RS, CRS, OS and OVS
    				copySetInfo.put(phase2.getKey(), new HashMap<String, HashSet<String>>());
    				HashMap<String, HashSet<String>> objSet = copySetInfo.get(phase2.getKey());
    				
    				Iterator it1 = setname.entrySet().iterator();
    				while (it1.hasNext()) {
    					Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it1.next();
    					objSet.put(phase3.getKey(), new HashSet<String>());
    					
    				}
    			}
    		}
    	}	
    	return copySetInfo;
    }
    
    //the place - for the merging of objects
    public HashMap<String, ObjNode> copyObjectToNewPlace (HashMap<String, ObjNode> src) {
    	HashMap<String, ObjNode> copyObjectInfo = new HashMap<String, ObjNode>();
    	
    	if (src != null) {
    		//the copier 
    		Iterator it = src.entrySet().iterator();
    		
    		while (it.hasNext()) {
    			Map.Entry<String, ObjNode> phase3 = (Map.Entry<String, ObjNode>)it.next();
    			ObjNode objIncl = ((ObjNode)phase3.getValue());
    			
    			if (objIncl != null) {
    				ObjNode on = new ObjNode(objIncl.name, objIncl.objType, true);
    				copyObjectInfo.put(phase3.getKey(), on);
    			}
    		}
    	}
    	
    	return copyObjectInfo;
    }
    
    /* Nobita code - a method */
    public HashMap<String, LinkedList<EdgeRep>> copyGraphToNewPlace (HashMap<String, LinkedList<EdgeRep>> src) {
    	HashMap<String, LinkedList<EdgeRep>> copyGraphInfo = new HashMap<String, LinkedList<EdgeRep>>();
    	if (src != null) {
    		//the copier
        	Iterator it = src.entrySet().iterator();
        	
        	while (it.hasNext()) {
        		Map.Entry<String, LinkedList<EdgeRep>> phase3 = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)phase3.getValue());
        		
        		if(edgeIncl != null) {
        			Iterator it1 = edgeIncl.iterator();
        			
        			//inserting into new DS
        			copyGraphInfo.put(phase3.getKey(), new LinkedList<EdgeRep>());
            		LinkedList<EdgeRep> edgeCopy = copyGraphInfo.get(phase3.getKey());
        			
        			while (it1.hasNext()) {
        				EdgeRep er = (EdgeRep)it1.next();
            			EdgeRep edgeDest = new EdgeRep(er.edgeType, er.desName, er.fieldName, true);
            			edgeDest.edgeName = er.edgeName;
            			edgeCopy.addLast(edgeDest);
        			}
        		}
        	}
    	}
    	
    	return copyGraphInfo;
    }
    
    /* Nobita code - a method */
    //the place - for the union of write sets into cumulative and others to blank
    public HashMap<String, HashMap<String, HashSet<String>>> copySetToSamePlace(HashMap<String, HashMap<String, HashSet<String>>> src) {
    	HashMap<String, HashMap<String, HashSet<String>>> copySetInfo = new HashMap<String, HashMap<String, HashSet<String>>>();
    	
    	if (src != null) {
    		Iterator it = src.entrySet().iterator();
    		
    		while (it.hasNext()) {
    			Map.Entry<String, HashMap<String, HashSet<String>>> phase2 = (Map.Entry<String, HashMap<String, HashSet<String>>>)it.next();
    			HashMap<String, HashSet<String>> setname = (HashMap<String, HashSet<String>>)phase2.getValue();
    			
    			if(phase2.getKey().equalsIgnoreCase("WS") || phase2.getKey().equalsIgnoreCase("MWS") || phase2.getKey().equalsIgnoreCase("CWS")) {
    				copySetInfo.put(phase2.getKey(), new HashMap<String, HashSet<String>>());
    				HashMap<String, HashSet<String>> objSet = copySetInfo.get(phase2.getKey());
    				
    				Iterator it1 = setname.entrySet().iterator();
    				while (it1.hasNext()) {
    					Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it1.next();
    					objSet.put(phase3.getKey(), new HashSet<String>());
    					
    					HashSet<String> set1 = objSet.get(phase3.getKey());
    					HashSet<String> set2 = (HashSet<String>)phase3.getValue();
    					
    					if (set1 != null && set2 != null) {
    						set1.addAll(set2);
    					}
    					
    				}
    				
    			}
    			else {
    				//for RS, CRS, OS and OVS
    				copySetInfo.put(phase2.getKey(), new HashMap<String, HashSet<String>>());
    				HashMap<String, HashSet<String>> objSet = copySetInfo.get(phase2.getKey());
    				
    				Iterator it1 = setname.entrySet().iterator();
    				while (it1.hasNext()) {
    					Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it1.next();
    					//System.out.println(phase3.getKey());
    					HashSet<String> set1 = null;
    					HashSet<String> set3 = null;
    					if (phase2.getKey().equalsIgnoreCase("RS")) {
    						set1 = (HashSet<String>)phase3.getValue();
    					}
    					if (phase2.getKey().equalsIgnoreCase("OVS")) {
    						set3 = (HashSet<String>)phase3.getValue();
    					}
    					
    					objSet.put(phase3.getKey(), new HashSet<String>());
    					
    					if (phase2.getKey().equalsIgnoreCase("RS")) {
    						HashSet<String> set2 = objSet.get(phase3.getKey());
    						set2.addAll(set1);
    					}
    					if (phase2.getKey().equalsIgnoreCase("OVS")) {
    						HashSet<String> set4 = objSet.get(phase3.getKey());
    						set4.addAll(set3);
    					}
    					
    				}
    			}
    		}
    	}	
    	return copySetInfo;
    }
    
    /* Nobita code - a method */
    //returns true or false to go for newxt iteration
    public boolean payBackBoolean (HashMap<String, LinkedList<EdgeRep>> src, HashMap<String, LinkedList<EdgeRep>> src2) {
    	boolean result = false;
    	if (src != null && src2 != null) {
	    		HashMap<String, LinkedList<EdgeRep>> payGraphInfo = src2;
	    		
	    		Iterator it = payGraphInfo.entrySet().iterator();
	    		
	    		while (it.hasNext()) {
	    			
	    			Map.Entry<String, LinkedList<EdgeRep>> phase3 = (Map.Entry<String, LinkedList<EdgeRep>>)it.next();
	        		LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)phase3.getValue());
	        		
	        		if (edgeIncl != null) {
	        			Iterator it1 = edgeIncl.iterator();
	        			
	        			//replacing started
	            		LinkedList<EdgeRep> src1 = src.get(phase3.getKey());
	            		
	            		while (it1.hasNext()) {
	            			EdgeRep er = (EdgeRep)it1.next();
	            			
	            			if (src1 != null) {
	            				Iterator it2 = src1.iterator();
	                			boolean found = false;
	                			
	                			while (it2.hasNext()) {
	                				EdgeRep er1 = (EdgeRep)it2.next();
	                				//System.out.println("Displaying the values: " + er.desName + ":" + er1.desName );
	                				if((er.desName.equalsIgnoreCase(er1.desName))) {
	                					found = true;
	                				}
	                				
	                				if(found) {
	                					break;
	                				}
	                			}
	                			
	                			if(!found) {
	                				src1.addLast(er);
	                				result = true;
	                			}
	            			}
	            		}
	        		}
	    			
	    		}
	    	
    	}
    	return result;
    }
    
    /* Nobita code */
    public void forOVS(HashSet<String> set1, HashMap<String, ObjNode> objDetail, HashMap<String, LinkedList<EdgeRep>> varInfo) {
    	
    	Iterator it = set1.iterator();
    	while (it.hasNext()) {
    		String str = (String)it.next();
    		if (str.length()>=2 && str.charAt(0) == 'z' && str.charAt(1) == 'x') {
    			
    			Iterator it2 = varInfo.entrySet().iterator();
    			while (it2.hasNext()) {
    				Map.Entry<String, LinkedList<EdgeRep>> phase3 = (Map.Entry<String, LinkedList<EdgeRep>>)it2.next();
    				
    				if (phase3.getKey().charAt(0) == 'o' && phase3.getKey().charAt(1) == 'b' && phase3.getKey().charAt(2) == 'j') {	
    					LinkedList<EdgeRep> edgeIncl = ((LinkedList<EdgeRep>)phase3.getValue());
    					if (edgeIncl != null) {
    						Iterator it3 = edgeIncl.iterator();
    						while (it3.hasNext()) {
    							EdgeRep er = (EdgeRep)it3.next();
    							//System.out.println(er.edgeName + " -ARE THEY EQUAL- " + str);
    							if (er.edgeName.equalsIgnoreCase(str)) {
    								ObjNode on = objDetail.get(er.desName);
    								on.counter = on.counter + 1;
    							}
    						}
    					}
    					
    				}
    			}
    			
    		}
    		else {
    			LinkedList<EdgeRep> ll = varInfo.get(str);
    			if (ll != null) {
	    			Iterator it1 = ll.iterator();
	    			while (it1.hasNext()) {
	    				EdgeRep er = (EdgeRep)it1.next();
	    				ObjNode on = objDetail.get(er.desName);
	    				on.counter = on.counter + 1;
	    			}
    			}
    		}
    		
    	}
    	
    	ObjNode on = objDetail.get("Obj-null");
    	on.counter = on.counter + 1;
    	
    	
    	//call that function here
    	if (objDetail != null) {
    		Iterator it1 = objDetail.entrySet().iterator();
    		
    		while (it1.hasNext()) {
    			Map.Entry<String, ObjNode> phase3 = (Map.Entry<String, ObjNode>)it1.next();
    			if (phase3.getKey().charAt(0) == 'S' || phase3.getKey().charAt(0) == 's') {
    				ObjNode on1 = (ObjNode)phase3.getValue();
    				on1.counter = on1.counter + 1;
    			}
    			String objType = ((ObjNode)phase3.getValue()).objType;
    			if (genClass.containsKey(objType)) {
    				ObjNode on1 = (ObjNode)phase3.getValue();
    				on1.counter = on1.counter + 1;
    			}
    		}
    	}
    }
    
    
    /* Nobita code */
    public void forOVS_Shared(HashMap<String, ObjNode> objDetail, HashMap<String, LinkedList<EdgeRep>> varInfo) {
    	
    }
    
    
    /* Nobita code */
    //for the second pass still to be edited
    public void printConstructorArgumentList_optimizer(X10ProcedureCall p, String closureName, int lineNo) {
    	
    	if ((currClass.size() > 0) && (currMethod.size() > 0)) {
	    	//getting the class and the method
	    	VarWithLineNo temp1 = currClass.peek();
	    	VarWithLineNo temp2 = currMethod.peek();

	    	//the graph
	    	HashMap<String, LinkedList<EdgeRep>> varInfo = null;
	    	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo4 = graphInfo.get(temp1.lineNo);
	    	if (methodInfo4 != null) {
	    		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo4.get(temp2.lineNo);
	    		if (lineInfo != null) {
	     			varInfo = lineInfo.get(lineNo);
	    		}
	    	}
	    	
	    	//the sets
	    	HashMap<String, HashMap<String, HashSet<String>>> setDetails = null;
	    	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
	    	if (setMethodInfo != null) {
	    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
	    		if (placeInfo != null) {
	    			setDetails = placeInfo.get(lineNo);
	    		}
	    	}
	    	
	    	//the objects
			HashMap<String, ObjNode> objDetail = null;
			HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo3 = objInfo.get(temp1.lineNo);
			if (methodInfo3 != null) {
				HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo3.get(temp2.lineNo);
				if (placeInfo != null) {
					 objDetail = placeInfo.get(lineNo);
				}
			}
	    	
	    	//filling up the hashmap's
	    	lineClosure.put(lineNo, closureName);
	    	closureVar.put(closureName, new HashMap <String, String>());
	    	closureObj.put(closureName, new HashMap<String, ForClosureObject>());
	   
	        List<Expr> l = p.arguments();
	        for (int i = 0; i < l.size(); i++) {
	            Expr e = l.get(i);
	            
	            if (e instanceof Local_c || e instanceof Special_c || e instanceof Field_c) {
	            	
	            	String varName = "";
	            	String varType = "";
	            	
	            	if (e instanceof Local_c ) {
	            		Local_c local = (Local_c)e;
	            		varName = local.name().toString();
	            		if (local.type().name() != null) {
	            			varType = local.type().name().toString();
	            		}
	            	}
	            	
	            	if (e instanceof Special_c) {
	            		Special_c special = (Special_c)e;
	            		varName = "this";
	            		varType = special.type().name().toString();
	            	}
	            	
	            	if (e instanceof Field_c) {
	            		Field_c field = (Field_c)e;
	            		if (temp2.name.equals("operator()")) {
	            			varName = field.fieldInstance().name().toString();
	            			varType = field.type().name().toString();
	            		}
	            		
	            	}
	            	
	            	
	            	if((varType != null) && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
	                		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
	                		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
	                		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
	            		
	            		LinkedList<EdgeRep> ll = varInfo.get(varName);
	            		LinkedList<ClassInfo> llci = classDetails.get(varType);
	            		HashMap<String, HashSet<String>> crSet = setDetails.get("CRS");
	            		
	            		HashMap <String, String> varClosure = closureVar.get(closureName);
	            		HashMap<String, ForClosureObject> objClosure = closureObj.get(closureName);
	            		
	            		if (ll != null) {
	            			if (ll.size() > 1) {
	            				varClosure.put(varName, "multiple");
	            			}
	            			else if (ll.size() == 1){
	            				EdgeRep er = (EdgeRep)ll.getFirst();
	            					
	            				ObjNode on = objDetail.get(er.desName);
	            				if (on != null) {
	            					if (on.counter > 0) {
	            						varClosure.put(varName, "multiple");
	            					}
	            					else if (on.counter == 0){
	            						varClosure.put(varName, er.desName);
	            						if (!(objClosure.containsKey(er.desName))) {
	            							ForClosureObject fco = new ForClosureObject(varName, false);
	            							
		            						objClosure.put(er.desName, fco);
		            						
		            						HashSet<String> crs = crSet.get(er.desName);
		            						
		            						//filtering to check the object not have field objects
		            						LinkedList<EdgeRep> ll1 = varInfo.get(er.desName);
		            						if (ll1 == null) {
		            							LinkedList<ForClosureObjectField> llFCOF = objClosure.get(er.desName).fieldDetails;
		            							
		            							Iterator it = crs.iterator();
		            							while (it.hasNext()) {
		            								String str = (String)it.next();
		            								
		            								String fieldType = "";
		            								String sendType = "";
		            								//iterating through class details linked list
		            								Iterator it1 = llci.iterator();
		            								while (it1.hasNext()) {
		            									ClassInfo ci = (ClassInfo)it1.next();
		            									if (ci.classifier.equalsIgnoreCase("field") && ci.name.equalsIgnoreCase(str)) {
		            										fieldType = ci.type;
		            										sendType = ci.x10Type;
		            										break;
		            									}
		            								}
		            								
		            								ForClosureObjectField fcof = new ForClosureObjectField(str, ("zztemp"+zztempNumber), sendType);
		            								zztempNumber++;
		            								llFCOF.add(fcof);
		            							}
		            							
		            						}
		            						else if (ll1 != null) { 
		            							LinkedList<ForClosureObjectField> llFCOF = objClosure.get(er.desName).fieldDetails;
		            							
		            							Iterator it = crs.iterator();
		            							while (it.hasNext()) {
		            								String str = (String)it.next();
		            								
		            								String fieldType = "";
		            								String sendType = "";
		            								//iterating through class details linked list
		            								Iterator it1 = llci.iterator();
		            								while (it1.hasNext()) {
		            									ClassInfo ci = (ClassInfo)it1.next();
		            									if (ci.classifier.equalsIgnoreCase("field") && ci.name.equalsIgnoreCase(str)) {
		            										fieldType = ci.type;
		            										sendType = ci.x10Type;
		            										break;
		            									}
		            								}
		            								
		            								if((fieldType != null) && !((fieldType.equalsIgnoreCase("Long")) || (fieldType.equalsIgnoreCase("Float")) || (fieldType.equalsIgnoreCase("String")) || (fieldType.equalsIgnoreCase("FileReader")) || (fieldType.equalsIgnoreCase("Printer")) || (fieldType.equalsIgnoreCase("Random")) || (fieldType.equalsIgnoreCase("FileWriter")) || 
		            				                		(fieldType.equalsIgnoreCase("Double")) || (fieldType.equalsIgnoreCase("Char")) || (fieldType.equalsIgnoreCase("PlaceGroup")) || (fieldType.equalsIgnoreCase("File")) || (fieldType.equalsIgnoreCase("FailedDynamicCheckException")) || (fieldType.equalsIgnoreCase("FinishState")) || (fieldType.equalsIgnoreCase("LongRange")) ||
		            				                		(fieldType.equalsIgnoreCase("Boolean")) || (fieldType.equalsIgnoreCase("Rail")) || (fieldType.equalsIgnoreCase("ArrayList")) || (fieldType.equalsIgnoreCase("Place")) || (fieldType.equalsIgnoreCase("Dist")) || (fieldType.equalsIgnoreCase("ArrayList")) || (fieldType.equalsIgnoreCase("Iterator")) || (fieldType.equalsIgnoreCase("Point")) || (fieldType.equalsIgnoreCase("Int")) ||
		            				                		(fieldType.equalsIgnoreCase("Array")) || (fieldType.equalsIgnoreCase("DistArray")) || (fieldType.equalsIgnoreCase("Region")) || (fieldType.equalsIgnoreCase("GlobalRef")))) {
		            									Iterator it2 = ll1.iterator();
		            									while (it2.hasNext()) {
		            										EdgeRep er1 = (EdgeRep)it2.next();
		            										if (er1.fieldName.equalsIgnoreCase(str)) {
		            											ObjNode on1 = objDetail.get(er1.desName);
		            											if (on1.counter > 0) {
		            												ForClosureObjectField fcof = new ForClosureObjectField(str, ("zztemp"+zztempNumber), sendType, true, er1.desName);
		            												zztempNumber++;
		        		            								llFCOF.add(fcof);
		        		            								break;
		            											}
		            											else {
		            												//call the function
		            												printConstructorArgumentList_optimizer1(varInfo, setDetails, objDetail, closureName, (varName+"."+er1.fieldName), er1.desName, fieldType);
		            												ForClosureObjectField fcof = new ForClosureObjectField(str, "NR", sendType, false, er1.desName);
		        		            								llFCOF.add(fcof);
		            												break;
		            											}
		            										}
		            									}
		            								}
		            								
		            								else {
			            								ForClosureObjectField fcof = new ForClosureObjectField(str, ("zztemp"+zztempNumber), sendType);
			            								zztempNumber++;
			            								llFCOF.add(fcof);
		            								}
		            							} 
		            						}
	            						}
	            					}
	            				}
	            			}
	            		}
	            	}
	            
	            }
	
	        }
	    }
    }
    
    
    /* Nobita code */
    //for the second pass still to be edited
    public void printConstructorArgumentList_optimizer1(HashMap<String, LinkedList<EdgeRep>> varInfo, HashMap<String, HashMap<String, HashSet<String>>> setDetails, 
    		HashMap<String, ObjNode> objDetail, String closureName, String varName, String objName, String objType) {
    	
    	HashMap<String, HashSet<String>> crSet = setDetails.get("CRS");
    	LinkedList<EdgeRep> ll = varInfo.get(objName);
    	LinkedList<ClassInfo> llci = classDetails.get(objType);
    	HashMap<String, ForClosureObject> objClosure = closureObj.get(closureName);
    	
    	if (closureObj != null && ll!= null) {
    		if (!(objClosure.containsKey(objName))) {
    			ForClosureObject fco = new ForClosureObject(varName, false);
    			objClosure.put(objName, fco);
    			
    			HashSet<String> crs = crSet.get(objName);
    			
    			LinkedList<ForClosureObjectField> llFCOF = objClosure.get(objName).fieldDetails;
    			Iterator it = crs.iterator();
    			while (it.hasNext()) {
    				String str = (String)it.next();
    				
    				String fieldType = "";
    				String sendType = "";
    				Iterator it1 = llci.iterator();
					while (it1.hasNext()) {
						ClassInfo ci = (ClassInfo)it1.next();
						if (ci.classifier.equalsIgnoreCase("field") && ci.name.equalsIgnoreCase(str)) {
							fieldType = ci.type;
							sendType = ci.x10Type;
							break;
						}
					}
					
					if((fieldType != null) && !((fieldType.equalsIgnoreCase("Long")) || (fieldType.equalsIgnoreCase("Float")) || (fieldType.equalsIgnoreCase("String")) || (fieldType.equalsIgnoreCase("FileReader")) || (fieldType.equalsIgnoreCase("Printer")) || (fieldType.equalsIgnoreCase("Random")) || (fieldType.equalsIgnoreCase("FileWriter")) || 
	                		(fieldType.equalsIgnoreCase("Double")) || (fieldType.equalsIgnoreCase("Char")) || (fieldType.equalsIgnoreCase("PlaceGroup")) || (fieldType.equalsIgnoreCase("File")) || (fieldType.equalsIgnoreCase("FailedDynamicCheckException")) || (fieldType.equalsIgnoreCase("FinishState")) || (fieldType.equalsIgnoreCase("LongRange")) ||
	                		(fieldType.equalsIgnoreCase("Boolean")) || (fieldType.equalsIgnoreCase("Rail")) || (fieldType.equalsIgnoreCase("ArrayList")) || (fieldType.equalsIgnoreCase("Place")) || (fieldType.equalsIgnoreCase("Dist")) || (fieldType.equalsIgnoreCase("ArrayList")) || (fieldType.equalsIgnoreCase("Iterator")) || (fieldType.equalsIgnoreCase("Point")) || (fieldType.equalsIgnoreCase("Int")) ||
	                		(fieldType.equalsIgnoreCase("Array")) || (fieldType.equalsIgnoreCase("DistArray")) || (fieldType.equalsIgnoreCase("Region")) || (fieldType.equalsIgnoreCase("GlobalRef")))) {
						Iterator it2 = ll.iterator();
						while (it2.hasNext()) {
							EdgeRep er1 = (EdgeRep)it2.next();
							if (er1.fieldName.equalsIgnoreCase(str)) {
								ObjNode on1 = objDetail.get(er1.desName);
								if (on1.counter > 0) {
									ForClosureObjectField fcof = new ForClosureObjectField(str, ("zztemp"+zztempNumber), sendType, true, er1.desName);
									zztempNumber++;
    								llFCOF.add(fcof);
    								break;
								}
								else {
									printConstructorArgumentList_optimizer1(varInfo, setDetails, objDetail, closureName, (varName+"."+er1.fieldName), er1.desName, fieldType);
									ForClosureObjectField fcof = new ForClosureObjectField(str, "NR", sendType, false, er1.desName);
    								llFCOF.add(fcof);
									break;
								}
							}
						}
					}
					else {
						ForClosureObjectField fcof = new ForClosureObjectField(str, ("zztemp"+zztempNumber), sendType);
						zztempNumber++;
						llFCOF.add(fcof);
					}
    			}
    			
    			
    		}
    	}
    	
    }
    
    /* Nobita code */
    //for the second pass still to be edited
    public void PrettyPrinterTemporary(String objName, HashMap<String, ForClosureObject> objClosure) {
    	if (!(savedObj.contains(objName))) {
    		savedObj.push(objName);
    		ForClosureObject fco = objClosure.get(objName);
			
			if (fco != null) {
				LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
				
				Iterator it1 = llFCOF.iterator();
				while (it1.hasNext()) {
					ForClosureObjectField fcof = (ForClosureObjectField)it1.next();
					
					if (fcof.tempStoredName.equalsIgnoreCase("NR")) {
						PrettyPrinterTemporary(fcof.fieldObjName, objClosure);
					}
					else {
						w.newline();
						String writerType = fcof.fieldType;
						if (fcof.fieldType.equalsIgnoreCase("x10.lang.Long")) {
							writerType = "long";
						}
						else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Float")) {
							writerType = "float";
						}
						else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Double")) {
							writerType = "double";
						}
						else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Char")) {
							writerType = "char";
						}
						else if (fcof.fieldType.equalsIgnoreCase("x10.lang.String")) {
							writerType = "java.lang.String";
						}
						else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Boolean")) {
							writerType = "boolean";
						}
						else {
							String tempTyCh = fcof.fieldType;
							String resultStr = " ";
							int j = tempTyCh.length()-1;
							for(int i = 0; i<=j ; i++) {
								if (i <= j-3) {
									int k = i;
									if (tempTyCh.charAt(i) == 'l' && tempTyCh.charAt(++k) == 'a' && 
											tempTyCh.charAt(++k) == 'n' && tempTyCh.charAt(++k) == 'g') {
										resultStr = resultStr + "core";
										i = i +3;
									}
									else {
										
										if (tempTyCh.charAt(i) == '[') {
											resultStr = resultStr + "<";
										}
										else if(tempTyCh.charAt(i) == ']') {
											resultStr = resultStr + ">";
										}
										else {
											resultStr = resultStr + tempTyCh.charAt(i);
										}
									}
								}
								else {
									if (tempTyCh.charAt(i) == '[') {
										resultStr = resultStr + "<";
									}
									else if(tempTyCh.charAt(i) == ']') {
										resultStr = resultStr + ">";
									}
									else {
										resultStr = resultStr + tempTyCh.charAt(i);
									}
								}
							}
							
							writerType = resultStr;
						}
						
						w.write("final ");
						w.write(writerType);
						w.write(" ");
						w.write(fcof.tempStoredName);
						w.write(" = ");
						w.write(fco.varName);
						w.write(".");
						w.write(fcof.fieldName);
						w.write(";");
						w.newline();
						
					}
				}
				
			}
    	}
    }
    
    /* Nobita code */
    //for the second pass still to be edited
    public void printConstructorArgumentList_pp1(Node_c c, X10ProcedureCall p, X10ConstructorInstance mi, Type type, boolean forceParams, String closureName) {
    	
    	/* Nobita code */
    	//getting the class and the method
    	VarWithLineNo temp1 = currClass.peek();
    	VarWithLineNo temp2 = currMethod.peek();

    	//the graph
    	HashMap<String, LinkedList<EdgeRep>> varInfo = null;
    	HashMap<Integer, HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>>> methodInfo4 = graphInfo.get(temp1.lineNo);
    	if (methodInfo4 != null) {
    		HashMap<Integer, HashMap<String, LinkedList<EdgeRep>>> lineInfo = methodInfo4.get(temp2.lineNo);
    		if (lineInfo != null) {
     			varInfo = lineInfo.get(lineNo);
    		}
    	}
    	
    	//the sets
    	HashMap<String, HashMap<String, HashSet<String>>> setDetails = null;
    	HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>>> setMethodInfo = setInfo.get(temp1.lineNo);
    	if (setMethodInfo != null) {
    		HashMap<Integer, HashMap<String, HashMap<String, HashSet<String>>>> placeInfo = setMethodInfo.get(temp2.lineNo);
    		if (placeInfo != null) {
    			setDetails = placeInfo.get(lineNo);
    		}
    	}
    	
    	//the objects
		HashMap<String, ObjNode> objDetail = null;
		HashMap<Integer, HashMap<Integer, HashMap<String, ObjNode>>> methodInfo3 = objInfo.get(temp1.lineNo);
		if (methodInfo3 != null) {
			HashMap<Integer, HashMap<String, ObjNode>> placeInfo = methodInfo3.get(temp2.lineNo);
			if (placeInfo != null) {
				 objDetail = placeInfo.get(lineNo);
			}
		}
    	/* Nobita code */
    	
    	w.write("(");
        w.begin(0);

        if (forceParams) {
        X10ClassType ct = mi.container().toClass();
        List<Type> ta = ct.typeArguments();
        boolean isJavaNative = type != null ? Emitter.isNativeRepedToJava(type) : false;
        if (ta != null && ta.size() > 0 && !isJavaNative) {
            printArgumentsForTypeParams(ta, p.arguments().size() == 0);
        }
        }

        //to fix the problem on comma
        boolean firstPass = true;
        List<Expr> l = p.arguments();
        for (int i = 0; i < l.size(); i++) {
            Expr e = l.get(i);
            
            //////////////////////////////////////////////////////////

    		
    		String varName = "";
        	String varType = "";
        	boolean path = true;
        	boolean suceed = false;
        	
        	//this is for first element print
        	boolean firstElementPrint = true;
        	
        	if (e instanceof Local_c) {
        		Local_c local = (Local_c)e;
        		varName = local.name().toString();
        		if (local.type().name() != null) {
        			varType = local.type().name().toString();
        		}
        	}
        	
        	if (e instanceof Special_c) {
        		Special_c special = (Special_c)e;
        		varName = "this";
        		varType = special.type().name().toString();
        	}
        	
        	if (e instanceof Field_c) {
        		Field_c field = (Field_c)e;
        		if (temp2.name.equals("operator()")) {
        			varName = field.fieldInstance().name().toString();
        			varType = field.type().name().toString();
        		}
        		else {
        			path = false;
        		}
        	}
        	
        	if (path) {
        		if((varType != null) && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
                		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
                		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
                		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
        			
        			HashMap <String, String> varClosure = closureVar.get(closureName);
            		HashMap<String, ForClosureObject> objClosure = closureObj.get(closureName);
            		
            		if (varClosure != null && objClosure != null) {
            			String varStatus = varClosure.get(varName);
            		
                		if (varStatus != null && !(varStatus.equalsIgnoreCase("multiple"))) {
                			
                			suceed = true;
                		}
            		}
        		}
        	}
    	
            /////////////////////////////////////////////////////////
            
            
        if (i < mi.formalTypes().size() && !suceed) { // FIXME This is a workaround
        	
        	/* Nobita code */
        	if (!firstPass) {
        		w.write(" ,");
        	}
        	
            Type castType = mi.formalTypes().get(i);
            Type defType = mi.def().formalTypes().get(i).get();
            TypeSystem xts = tr.typeSystem();
            if (isString(e.type()) && !isString(castType)) {

                w.write("(");
                er.printType(castType, 0);
                w.write(")");

                if (xts.isParameterType(castType)) {
                    w.write(X10_RTT_TYPES);
                    w.write(".conversion(");
                    new RuntimeTypeExpander(er, Types.baseType(castType)).expand(tr);
                    w.write(",");
                } else {
                    w.write("(");
                }
                c.print(e, w, tr);
                w.write(")");
            } else if (useSelfDispatch && !castType.typeEquals(e.type(), tr.context())) {
                w.write("(");
                if (needExplicitBoxing(e.type()) && isBoxedType(defType)) {
                    er.printBoxConversion(e.type());
                } else {
                    // TODO:CAST
                    w.write("(");
                    // XTENLANG-2895 use erasure to implement co/contra-variance of function type
                    // XTENLANG-3259 to avoid post-compilation error with Java constructor with Comparable parameter.
                    er.printType(castType, (xts.isFunctionType(castType) || Emitter.isNativeRepedToJava(castType)) ? 0 : PRINT_TYPE_PARAMS);
                    w.write(")");
                }
                w.write("(");       // printBoxConvesion assumes parentheses around expression
                c.print(e, w, tr);
                w.write(")");
                w.write(")");
            } else {
                if (needExplicitBoxing(castType) && defType.isParameterType()) {
                    er.printBoxConversion(castType);
                    w.write("(");
                    c.print(e, w, tr);
                    w.write(")");
                } else {
                    c.print(e, w, tr);
                }
            }
            
            //nobita code
            firstPass = false;
        } 
        else if (i < mi.formalTypes().size() && suceed) { // FIXME This is a workaround
        	suceed = false;
        	
        	HashMap<String, ForClosureObject> objClosure = closureObj.get(closureName);
        	
			LinkedList<EdgeRep> ll = varInfo.get(varName);
			if (ll != null && ll.size()>0) {
				EdgeRep er = (EdgeRep)ll.getFirst();
				
				ForClosureObject fco = objClosure.get(er.desName);
				if (fco != null && !(savedObj.contains(er.desName))) {
					savedObj.push(er.desName);
					LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
					//firstElementPrint = false;
					if (llFCOF != null && llFCOF.size()>0) {
						//firstElementPrint = true;
						if (!firstPass /* && firstElementPrint*/)  {
		        			w.write(" ,");
		        		}
						Iterator it = llFCOF.iterator();
						boolean first = true;
						
						while (it.hasNext()) {
							ForClosureObjectField fcof = (ForClosureObjectField)it.next();
							if (fcof.tempStoredName.equalsIgnoreCase("NR")) {
								// call the another function
								first = printConstructorArgumentList_pp1_1(closureName, first, objClosure, fcof.fieldObjName);
								firstPass = false;
							}
							else {
								//firstElementPrint = false;
								if (!fcof.tempStoredName.equalsIgnoreCase("")) {
									//firstElementPrint = true;
									firstPass = false;
									if (first) {
										first = false;
										w.write(fcof.tempStoredName);
									}
									else {
										w.write(" ,");
										w.write(fcof.tempStoredName);
									}
								}
							}
						}
					}
				}
			}
        }
        else {
			if (!firstPass /* && firstElementPrint */) {
    			w.write(" ,");
    		}
			firstPass = false;
                c.print(e, w, tr);
        }
        
            if (isMutableStruct(e.type())) {
                w.write(".clone()");
            }

            //if ((i != l.size() - 1) && firstElementPrint && noPass) {
              //  w.write(", ");
            //}
        }

        printExtraArgments(mi);

        w.end();
        w.write(")");
    }
    
    /* Nobita code */
    //for the second pass still to be edited
    public boolean printConstructorArgumentList_pp1_1(String closureName, boolean first, HashMap<String, ForClosureObject> objClosure, String objName) {
    	if (objClosure != null && !(savedObj.contains(objName))) {
    		savedObj.push(objName);
    		
    		ForClosureObject fco = objClosure.get(objName);
    		if (fco != null) {
    			
    			LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
    			if (llFCOF != null && llFCOF.size()>0) {
    				Iterator it = llFCOF.iterator();
					
					while (it.hasNext()) {
						ForClosureObjectField fcof = (ForClosureObjectField)it.next();
						if (fcof.tempStoredName.equalsIgnoreCase("NR")) {
							// call the another function
							first = printConstructorArgumentList_pp1_1(closureName, first, objClosure, fcof.fieldObjName);
						}
						else {
							
							if (!fcof.tempStoredName.equalsIgnoreCase("")) {
								if (first) {
									first = false;
									w.write(fcof.tempStoredName);
								}
								else {
									w.write(" ,");
									w.write(fcof.tempStoredName);
								}
							
							}
						}
					}
    			}
    		}
    		
    	}
    	return first;
    }
    
    /* Nobita code */
    //for the second pass still to be edited
    public List<String> printConstructorFormals_pp1(X10ConstructorDecl_c n, boolean forceParams) {
    	
    	/* Nobita code */
    	VarWithLineNo temp1 = currClass.peek();
    	/* Nobita code */
    	
    	w.write("(");

        w.begin(0);

        X10ConstructorDef ci = n.constructorDef();
        X10ClassType ct = Types.get(ci.container()).toClass();
        List<String> params = new ArrayList<String>();

        if (forceParams) {
        for (Iterator<ParameterType> i = ct.x10Def().typeParameters().iterator(); i.hasNext();) {
            ParameterType p = i.next();
            w.write("final ");
            w.write(X10_RTT_TYPE);
            w.write(" ");
            String name = Emitter.mangleParameterType(p);
            w.write(name);
            if (i.hasNext() || n.formals().size() > 0) {
                w.write(", ");
            }
            params.add(name);
        }
        }

        boolean firstPass = true;
        for (Iterator<Formal> i = n.formals().iterator(); i.hasNext();) {
            Formal f = i.next();
            
            /* Nobita code */
            
            //for the first element
            //boolean firstPass = true;
            
           // System.out.println("I AM PRINTING FORMAL NAME: " + f.name().toString() + " its type: " + f.type().nameString());
            String varName = f.name().toString();
        	String varType = f.type().nameString();
        	
        	if((varType != null) && !((varType.equalsIgnoreCase("Long")) || (varType.equalsIgnoreCase("Float")) || (varType.equalsIgnoreCase("String")) || (varType.equalsIgnoreCase("FileReader")) || (varType.equalsIgnoreCase("Printer")) || (varType.equalsIgnoreCase("Random")) || (varType.equalsIgnoreCase("FileWriter")) || 
            		(varType.equalsIgnoreCase("Double")) || (varType.equalsIgnoreCase("Char")) || (varType.equalsIgnoreCase("PlaceGroup")) || (varType.equalsIgnoreCase("File")) || (varType.equalsIgnoreCase("FailedDynamicCheckException")) || (varType.equalsIgnoreCase("FinishState")) || (varType.equalsIgnoreCase("LongRange")) ||
            		(varType.equalsIgnoreCase("Boolean")) || (varType.equalsIgnoreCase("Rail")) || (varType.equalsIgnoreCase("Place")) || (varType.equalsIgnoreCase("Dist")) || (varType.equalsIgnoreCase("ArrayList")) || (varType.equalsIgnoreCase("Iterator")) || (varType.equalsIgnoreCase("Point")) || (varType.equalsIgnoreCase("Int")) ||
            		(varType.equalsIgnoreCase("Array")) || (varType.equalsIgnoreCase("DistArray")) || (varType.equalsIgnoreCase("Region")) || (varType.equalsIgnoreCase("GlobalRef")))) {
        		
        		HashMap <String, String> varClosure = closureVar.get(temp1.name);
        		HashMap<String, ForClosureObject> objClosure = closureObj.get(temp1.name);
        		
        		if (varClosure != null && objClosure != null) {
        			
        			String varStatus = "";
        			if (varName.equals("out$$")) {
        				
        				if (varClosure.containsKey("out$$")) {
        					varStatus = varClosure.get(varName);
        				}
        				else {
        					varStatus = varClosure.get("this");
        				}
        			}
        			else {
        				varStatus = varClosure.get(varName);
        			}
        			
        			if (varStatus!= null && !(varStatus.equalsIgnoreCase("multiple"))) {
        				
        				ForClosureObject fco = objClosure.get(varStatus);
        				
        				if (fco != null && !(savedObj.contains(varStatus))) {
        					savedObj.push(varStatus);
        					
        					LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
        					//firstElementPrint = false;
        					if (llFCOF != null && llFCOF.size()>0) {
        						//firstElementPrint = true;
        						if (!firstPass) {
        		        			w.write(" ,");
        		        		}
        						firstPass = false;
        						
        						Iterator it = llFCOF.iterator();
        						boolean first = true;
        						
        						while (it.hasNext()) {
        							ForClosureObjectField fcof = (ForClosureObjectField)it.next();
        							if (fcof.tempStoredName.equalsIgnoreCase("NR")) {
        								//recursive fn here
        								first = printConstructorFormals_pp1_1(first, objClosure, fcof.fieldObjName);
        							}
        							else {
        								if (first) {
        									first = false;
        									
        									String writerType = fcof.fieldType;
        									
        									if (fcof.fieldType.equalsIgnoreCase("x10.lang.Long")) {
        										writerType = "long";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Float")) {
        										writerType = "float";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Double")) {
        										writerType = "double";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Char")) {
        										writerType = "char";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.String")) {
        										writerType = "java.lang.String";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Boolean")) {
        										writerType = "boolean";
        									}
        									else {
        										String tempTyCh = fcof.fieldType;
        										String resultStr = " ";
        										int j = tempTyCh.length()-1;
        										for(int p = 0; p<=j ; p++) {
        											if (p <= j-3) {
        												int k = p;
        												if (tempTyCh.charAt(p) == 'l' && tempTyCh.charAt(++k) == 'a' && 
        														tempTyCh.charAt(++k) == 'n' && tempTyCh.charAt(++k) == 'g') {
        													resultStr = resultStr + "core";
        													p = p +3;
        												}
        												else {
        													
        													if (tempTyCh.charAt(p) == '[') {
        														resultStr = resultStr + "<";
        													}
        													else if(tempTyCh.charAt(p) == ']') {
        														resultStr = resultStr + ">";
        													}
        													else {
        														resultStr = resultStr + tempTyCh.charAt(p);
        													}
        												}
        											}
        											else {
        												if (tempTyCh.charAt(p) == '[') {
        													resultStr = resultStr + "<";
        												}
        												else if(tempTyCh.charAt(p) == ']') {
        													resultStr = resultStr + ">";
        												}
        												else {
        													resultStr = resultStr + tempTyCh.charAt(p);
        												}
        											}
        										}
        										
        										writerType = resultStr;
        									}
        									
        									w.write("final ");
        									w.write(writerType);
        									w.write(" ");
        									w.write(fcof.tempStoredName);
        								}
        								else {
        									
        									String writerType = fcof.fieldType;
        									
        									if (fcof.fieldType.equalsIgnoreCase("x10.lang.Long")) {
        										writerType = "long";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Float")) {
        										writerType = "float";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Double")) {
        										writerType = "double";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Char")) {
        										writerType = "char";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.String")) {
        										writerType = "java.lang.String";
        									}
        									else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Boolean")) {
        										writerType = "boolean";
        									}
        									else {
        										String tempTyCh = fcof.fieldType;
        										String resultStr = " ";
        										int j = tempTyCh.length()-1;
        										for(int p = 0; p<=j ; p++) {
        											if (p <= j-3) {
        												int k = p;
        												if (tempTyCh.charAt(p) == 'l' && tempTyCh.charAt(++k) == 'a' && 
        														tempTyCh.charAt(++k) == 'n' && tempTyCh.charAt(++k) == 'g') {
        													resultStr = resultStr + "core";
        													p = p +3;
        												}
        												else {
        													
        													if (tempTyCh.charAt(p) == '[') {
        														resultStr = resultStr + "<";
        													}
        													else if(tempTyCh.charAt(p) == ']') {
        														resultStr = resultStr + ">";
        													}
        													else {
        														resultStr = resultStr + tempTyCh.charAt(p);
        													}
        												}
        											}
        											else {
        												if (tempTyCh.charAt(p) == '[') {
        													resultStr = resultStr + "<";
        												}
        												else if(tempTyCh.charAt(p) == ']') {
        													resultStr = resultStr + ">";
        												}
        												else {
        													resultStr = resultStr + tempTyCh.charAt(p);
        												}
        											}
        										}
        										
        										writerType = resultStr;
        									}
        									
        									w.write(", final ");
        									w.write(writerType);
        									w.write(" ");
        									w.write(fcof.tempStoredName);
        								}
        							}
        						}
        					}
        				}
        			}
        			else {
        				if (!firstPass) {
                			w.write(" ,");
                		}
        				n.print(f, w, tr);
        				firstPass = false;
        			}
        		}
        		else {
        			if (!firstPass) {
            			w.write(" ,");
            		}
        			n.print(f, w, tr);
        			firstPass = false;
        		}
        	}
        	else {
        		if (!firstPass) {
        			w.write(" ,");
        		}
        		n.print(f, w, tr);
        		firstPass = false;
        	}

            /* Nobita code */

           // if (i.hasNext() && firstElementPrint) {
             //   w.write(", ");
            //}
        }

        printExtraFormals(n);

        w.end();
        w.write(")");

        return params;
    }
    
    
    /* Nobita code */
    //for the second pass still to be edited
    public boolean printConstructorFormals_pp1_1 (boolean first, HashMap<String, ForClosureObject> objClosure, String objName) {
    	if (objClosure != null && !(savedObj.contains(objName))) {
    		savedObj.push(objName);
    		
    		ForClosureObject fco = objClosure.get(objName);
    		if (fco != null) {
    			
    			LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
    			if (llFCOF != null && llFCOF.size()>0) {
    				Iterator it = llFCOF.iterator();
					
					while (it.hasNext()) {
						ForClosureObjectField fcof = (ForClosureObjectField)it.next();
						if (fcof.tempStoredName.equalsIgnoreCase("NR")) {
							//recursive fn here
							first = printConstructorFormals_pp1_1(first, objClosure, fcof.fieldObjName);
						}
						else {
							if (first) {
								first = false;
								
								String writerType = fcof.fieldType;
								
								if (fcof.fieldType.equalsIgnoreCase("x10.lang.Long")) {
									writerType = "long";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Float")) {
									writerType = "float";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Double")) {
									writerType = "double";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Char")) {
									writerType = "char";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.String")) {
									writerType = "java.lang.String";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Boolean")) {
									writerType = "boolean";
								}
								else {
									String tempTyCh = fcof.fieldType;
									String resultStr = " ";
									int j = tempTyCh.length()-1;
									for(int p = 0; p<=j ; p++) {
										if (p <= j-3) {
											int k = p;
											if (tempTyCh.charAt(p) == 'l' && tempTyCh.charAt(++k) == 'a' && 
													tempTyCh.charAt(++k) == 'n' && tempTyCh.charAt(++k) == 'g') {
												resultStr = resultStr + "core";
												p = p +3;
											}
											else {
												
												if (tempTyCh.charAt(p) == '[') {
													resultStr = resultStr + "<";
												}
												else if(tempTyCh.charAt(p) == ']') {
													resultStr = resultStr + ">";
												}
												else {
													resultStr = resultStr + tempTyCh.charAt(p);
												}
											}
										}
										else {
											if (tempTyCh.charAt(p) == '[') {
												resultStr = resultStr + "<";
											}
											else if(tempTyCh.charAt(p) == ']') {
												resultStr = resultStr + ">";
											}
											else {
												resultStr = resultStr + tempTyCh.charAt(p);
											}
										}
									}
									
									writerType = resultStr;
								}
								
								w.write("final ");
								w.write(writerType);
								w.write(" ");
								w.write(fcof.tempStoredName);
							}
							else {
								String writerType = fcof.fieldType;
								
								if (fcof.fieldType.equalsIgnoreCase("x10.lang.Long")) {
									writerType = "long";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Float")) {
									writerType = "float";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Double")) {
									writerType = "double";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Char")) {
									writerType = "char";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.String")) {
									writerType = "java.lang.String";
								}
								else if (fcof.fieldType.equalsIgnoreCase("x10.lang.Boolean")) {
									writerType = "boolean";
								}
								else {
									String tempTyCh = fcof.fieldType;
									String resultStr = " ";
									int j = tempTyCh.length()-1;
									for(int p = 0; p<=j ; p++) {
										if (p <= j-3) {
											int k = p;
											if (tempTyCh.charAt(p) == 'l' && tempTyCh.charAt(++k) == 'a' && 
													tempTyCh.charAt(++k) == 'n' && tempTyCh.charAt(++k) == 'g') {
												resultStr = resultStr + "core";
												p = p +3;
											}
											else {
												
												if (tempTyCh.charAt(p) == '[') {
													resultStr = resultStr + "<";
												}
												else if(tempTyCh.charAt(p) == ']') {
													resultStr = resultStr + ">";
												}
												else {
													resultStr = resultStr + tempTyCh.charAt(p);
												}
											}
										}
										else {
											if (tempTyCh.charAt(p) == '[') {
												resultStr = resultStr + "<";
											}
											else if(tempTyCh.charAt(p) == ']') {
												resultStr = resultStr + ">";
											}
											else {
												resultStr = resultStr + tempTyCh.charAt(p);
											}
										}
									}
									
									writerType = resultStr;
								}
								
								w.write(", final ");
								w.write(writerType);
								w.write(" ");
								w.write(fcof.tempStoredName);
							}
						}
					}
    			}
    		}
    		
    	}
    	return first;
    }
    
    /* Nobita code */
    //for the second pass still to be edited
    public void prettyPrintCons(HashMap<String, ForClosureObject> objClosure, String objName, String lhsName) {
    	ForClosureObject fco = objClosure.get(objName);
    	if (fco != null) {
    		LinkedList<ForClosureObjectField> llFCOF = fco.fieldDetails;
    		if (llFCOF != null && llFCOF.size()>0) {
    			Iterator it = llFCOF.iterator();
    			
    			while (it.hasNext()) {
    				ForClosureObjectField fcof = (ForClosureObjectField)it.next();
    				
    				if (fcof.tempStoredName.equalsIgnoreCase("NR")) {
    					
    					if (!(savedObj.contains(fcof.fieldObjName))) {
    						String fieldIns = "";
    						if (packageName.containsKey(fcof.fieldType)) {
    							fieldIns = "new "+packageName.get(fcof.fieldType)+"."+(fcof.fieldType)+"((java.lang.System[]) null)."+packageName.get(fcof.fieldType)+"$"+(fcof.fieldType)+"$$init$S(";
    						}
    						else {
    							fieldIns = "new "+(fcof.fieldType)+"((java.lang.System[]) null)."+(fcof.fieldType)+"$$init$S(";
    						}
							
							LinkedList<ClassInfo> llci1 = classDetails.get(fcof.fieldType);
							if (llci1 != null) {
								Iterator it1 = llci1.iterator();
								while (it1.hasNext()) {
									ClassInfo ci = (ClassInfo)it1.next();
	        	            		if (ci.classifier.equalsIgnoreCase("constructor") && ci.name.equalsIgnoreCase("0")) {
	        	            			fieldIns = fieldIns + ci.type;
	        	            			break;
	        	            		}
								}
							}
							
							//add to save obj field
							savedObjDet.put(fcof.fieldObjName, lhsName +"."+fcof.fieldName);
							savedObj.push(fcof.fieldObjName);
							
							fieldIns = fieldIns + ")";
							
							w.write(";");
							w.newline();
							
	
							w.write(lhsName);
							w.write(".");
							w.write(fcof.fieldName);
							w.write(" ");
							w.write("=");
							w.write(" ");
							w.write(fieldIns);
							
							//call the recursive function here
							String lhsSendName = lhsName+"."+fcof.fieldName;
							prettyPrintCons(objClosure, fcof.fieldObjName, lhsSendName);
							
    					}
    					else {
    						String fieldIns = savedObjDet.get(fcof.fieldObjName);
    						w.write(lhsName);
							w.write(".");
							w.write(fcof.fieldName);
							w.write(" ");
							w.write("=");
							w.write(" ");
							w.write(fieldIns);
    						
    					}
    				}
    				else {
    					w.write(";");
						w.newline();
						
						w.write(lhsName);
						w.write(".");
						w.write(fcof.fieldName);
						w.write(" ");
						w.write("=");
						w.write(" ");
						w.write(fcof.tempStoredName);
    				}
    			}
    		}
    	}
    }
    
    /* Nobita code */
    //for the inter-procedure analysis
    public void paraObjCollectSet(HashSet<String> set1, String objName, HashMap<String, LinkedList<EdgeRep>> varInfo) {
    	if (varInfo != null && !objName.equalsIgnoreCase("") && !set1.contains(objName)) {
    		LinkedList<EdgeRep> ller = varInfo.get(objName);
    		
    		if (ller != null) {
    			Iterator it = ller.iterator();
    			while (it.hasNext()) {
    				EdgeRep er = (EdgeRep)it.next();
					if (!er.desName.equalsIgnoreCase("Obj-null")) {
						set1.add(er.desName);
						if (varInfo.containsKey(er.desName)) {
							paraObjCollectSet(set1, er.desName, varInfo);
						}
					}
    			}
    		}
    	}
    	
    }
    
    /* Nobita code */
    //for the inter-procedure analysis
    public void fixSizeOfParam(LinkedList<String> paraList, HashMap<String, HashSet<String>> paraObjSet, String className, 
    		String methodName,  HashMap<String, LinkedList<EdgeRep>> varInfo, String uniqValue, boolean hasObjPara, boolean hasNullPara) {

    	//to get the method details from the object class
    	LinkedList<ClassInfo> llfi = classDetails.get(className);
    	LinkedList<ClassInfo> llMethodPara = null;
    	if (llfi != null) {
			if (hasObjPara) {
				if (hasNullPara) {
					Iterator it = llfi.iterator();
					while (it.hasNext()) {
						ClassInfo ci = (ClassInfo)it.next();
						if (ci.classifier.equalsIgnoreCase("method") && ci.name.equalsIgnoreCase(methodName)) {
							//TODO: Taking string as non-NUll. If not do changes in the code.
							if (ci.uniqueId.length() == uniqValue.length()) {
								int length = uniqValue.length();
								int index = 0;
								String storedId = ci.uniqueId;
								while (length > 0) {
									if (storedId.charAt(index) == uniqValue.charAt(index)) {
										length--;
									}
									else if (storedId.charAt(index) != 'a' && uniqValue.charAt(index) == 'N') {
										length--;
									}
									else {
										length = -1;
									}
									
									if (length == 0) {
										llMethodPara = ci.methodPara;
									}
								}
								
								if (llMethodPara != null) {
									break;
								}
							}
						}
					}
				}
				else {
					Iterator it = llfi.iterator();
					while (it.hasNext()) {
						ClassInfo ci = (ClassInfo)it.next();
						if (ci.classifier.equalsIgnoreCase("method") && ci.name.equalsIgnoreCase(methodName) &&  ci.uniqueId.equalsIgnoreCase(uniqValue)) {
							llMethodPara = ci.methodPara;
							break;
						}
					}
				}
			}
			else {
				Iterator it = llfi.iterator();
				while (it.hasNext()) {
					ClassInfo ci = (ClassInfo)it.next();
					if (ci.classifier.equalsIgnoreCase("method") && ci.name.equalsIgnoreCase(methodName) && (ci.uniqueId == null || ci.uniqueId.equalsIgnoreCase(""))) {
						llMethodPara = ci.methodPara;
						break;
					}
				}
			}
	    	
			
			//Nobita code - for setting the structure of the input parameter
			HashMap<String, String> sharedObjDetails = new HashMap<String, String>();
			if (llMethodPara !=  null) {
				int index = 0;
				
				Iterator it = llMethodPara.iterator();
				while (it.hasNext()) {
					int pat = index;
					//get the shared object details with other parameter!
					String paraVarName = "";
					paraVarName = paraList.get(index);
					HashSet<String> sharedObjSet = new HashSet<String>();
					
					if (!paraVarName.equalsIgnoreCase("null")) {
						Iterator it2 = paraObjSet.entrySet().iterator();
						while (it2.hasNext()) {
							HashSet<String> seta1 = paraObjSet.get(paraVarName);
							HashSet<String> set1 = new HashSet<String>();
							if (seta1 != null) {
								Iterator it1 = seta1.iterator();
								while (it1.hasNext()) {
									String str = (String)it1.next();
									set1.add(str);
								}
							}
							Map.Entry<String, HashSet<String>> phase3 = (Map.Entry<String, HashSet<String>>)it2.next();
							if (!phase3.getKey().equalsIgnoreCase(paraVarName)) {
								HashSet<String> set2 = phase3.getValue();
								
								if (set2 != null) {
									set1.retainAll(set2);
								}
								sharedObjSet.addAll(set1);
							}
						}
					}
					
					//now to update the structure of the parameter!
					ClassInfo ci = (ClassInfo)it.next();
					if (!objParaGraphInfo.containsKey(ci.classifier)) {
						objParaGraphInfo.put(ci.classifier, new LinkedList<EdgeRep>());
					}
					
					LinkedList<EdgeRep> calleeLL = objParaGraphInfo.get(ci.classifier);
					LinkedList<EdgeRep> callerLL = varInfo.get(paraVarName);
					
					if (calleeLL != null && callerLL != null) {
						
						Iterator it1 = callerLL.iterator();
						while (it1.hasNext()) {
							EdgeRep er = (EdgeRep)it1.next();
							if (er.desName.equalsIgnoreCase("Obj-null")) {
								Iterator it3 = calleeLL.iterator();
								boolean found = false;
								while (it3.hasNext()) {
									EdgeRep er1 = (EdgeRep)it3.next();
									if (er1.desName.equalsIgnoreCase("Obj-null")) {
										found = true;
										break;
									}
								}
								if (!found) {
									EdgeRep er1 = new EdgeRep("P", "Obj-null");
									calleeLL.add(er1);
								}
							}
							else {
								String calleeObjName_1 = "";
								boolean shared = sharedObjSet.contains(er.desName);
								String shName = "";
								if (shared) {
									if (sharedObjDetails.containsKey(er.desName)) {
										shName = sharedObjDetails.get(er.desName);
									}
									else {
										pat++;
										String name = "S"+ci.classifier+pat;
										sharedObjDetails.put(er.desName, name);
										
									}
									
									shName = sharedObjDetails.get(er.desName);
								}
	
								Iterator it3 = calleeLL.iterator();
								boolean found = false;
								while (it3.hasNext()) {
									EdgeRep er1 = (EdgeRep)it3.next();
									if (!er1.desName.equalsIgnoreCase("Obj-null")) {
										if (shared) {
											if (er1.desName.charAt(0) == 'S') {
												sharedObjDetails.put(er.desName, er1.desName);
											}
											else {
												er1.desName = shName;
											}
										}
										calleeObjName_1 = er1.desName;
										found = true;
										break;
									}
								}
								if (!found) {
									if (shared) {
										EdgeRep er1 = new EdgeRep("P", shName);
										calleeLL.add(er1);
										calleeObjName_1 = shName;
									}
									else {
										pat++;
										EdgeRep er1 = new EdgeRep("P", (ci.classifier+pat));
										calleeLL.add(er1);
										calleeObjName_1 = ci.classifier+pat;
									}
								}
								
								//call the recursive function here
								fixSizeOfParamFieldObj(er.desName, calleeObjName_1, varInfo, pat, sharedObjDetails, sharedObjSet);
							}
						}
					}
					
					if (paraVarName.equalsIgnoreCase("null")) {
						Iterator it3 = calleeLL.iterator();
						boolean found = false;
						while (it3.hasNext()) {
							EdgeRep er1 = (EdgeRep)it3.next();
							if (er1.desName.equalsIgnoreCase("Obj-null")) {
								found = true;
								break;
							}
						}
						if (!found) {
							EdgeRep er1 = new EdgeRep("P", "Obj-null");
							calleeLL.add(er1);
						}
					}
					
					index++;
				}
			}
    	}
    }
    
    /* Nobita code */
    //for the inter-procedure analysis
    public void fixSizeOfParamFieldObj (String callerObjName, String calleeObjName, HashMap<String, LinkedList<EdgeRep>> varInfo, 
    		int pat, HashMap<String, String> sharedObjDetails, HashSet<String> sharedObjSet) {
    	if (!objParaGraphInfo.containsKey(calleeObjName)) {
			objParaGraphInfo.put(calleeObjName, new LinkedList<EdgeRep>());
		}
		LinkedList<EdgeRep> calleeLL = objParaGraphInfo.get(calleeObjName);
		LinkedList<EdgeRep> callerLL = varInfo.get(callerObjName);
		
		edgeNumber++;
		if (calleeLL != null && callerLL != null) {
			Iterator it = callerLL.iterator();
			while (it.hasNext()) {
				EdgeRep er = (EdgeRep)it.next();
				if (!er.fieldName.equalsIgnoreCase("")) {
					if (er.desName.equalsIgnoreCase("Obj-null")) {
						Iterator it1 = calleeLL.iterator();
						boolean found = false;
						String edgeName = "";
						while (it1.hasNext()) {
							EdgeRep er1 = (EdgeRep)it1.next();
							//TODO: apply optimization for the below two if-conditions
							if (er1.desName.equalsIgnoreCase("Obj-null") && er1.fieldName.equalsIgnoreCase(er.fieldName)) {
								found = true;
								break;
							}
							if (!er1.desName.equalsIgnoreCase("Obj-null") && er1.fieldName.equalsIgnoreCase(er.fieldName)) {
								if (er1.edgeName.equalsIgnoreCase("")) {
									//edgeNumber++;
									edgeName = "zx"+edgeNumber;
									er1.edgeName = edgeName;
								}
								else {
									edgeName = er1.edgeName;
								}
							}
						}
						if (!found) {
							EdgeRep er1 = new EdgeRep("F", "Obj-null", er.fieldName);
							er1.edgeName = edgeName;
							calleeLL.add(er1);
						}
					}
					else {
						String calleeObjName_1 = ""; 
						boolean shared = sharedObjSet.contains(er.desName);
						String shName = "";
						if (shared) {
							if (sharedObjDetails.containsKey(er.desName)) {
								shName = sharedObjDetails.get(er.desName);
							}
							else {
								pat++;
								String name = "SF"+calleeObjName+pat;
								sharedObjDetails.put(er.desName, name);
								
							}
							
							shName = sharedObjDetails.get(er.desName);
						}
						
						Iterator it3 = calleeLL.iterator();
						boolean found = false;
						String edgeName = "";
						while (it3.hasNext()) {
							EdgeRep er1 = (EdgeRep)it3.next();
							if (!er1.desName.equalsIgnoreCase("Obj-null") && er1.fieldName.equalsIgnoreCase(er.fieldName)) {
								if (shared) {
									if (er1.desName.charAt(0) == 'S') {
										sharedObjDetails.put(er.desName, er1.desName);
									}
									else {
										er1.desName = shName;
									}
								}
								calleeObjName_1 = er1.desName;
								found = true;
								break;
							}
							
							 if (er1.desName.equalsIgnoreCase("Obj-null") && er1.fieldName.equalsIgnoreCase(er.fieldName)) {
								if (er1.edgeName.equalsIgnoreCase("")) {
									//edgeNumber++;
									edgeName = "zx"+edgeNumber;
									er1.edgeName = edgeName;
								}
								else {
									edgeName = er1.edgeName;
								}
							}
							
						}
						
						if (!found) {
							if (shared) {
								EdgeRep er2 = new EdgeRep("F", shName, er.fieldName);
								er2.edgeName = edgeName;
								calleeLL.add(er2);
								calleeObjName_1 = shName;
							}
							else {
								pat++;
								EdgeRep er2 = new EdgeRep("F", "F"+calleeObjName+"f"+pat, er.fieldName);
								er2.edgeName = edgeName;
								calleeLL.add(er2);
								calleeObjName_1 = "F"+calleeObjName+"f"+pat;
							}
						}
						
						//call the function here
						if (!callerObjName.equalsIgnoreCase(er.desName)) {
							fixSizeOfParamFieldObj(er.desName, calleeObjName_1, varInfo, pat, sharedObjDetails, sharedObjSet);
						}
					}
				}
			}
		}
    }
    
    /* Nobita code */
    //for the inter-procedure analysis
    public void fnCallSetUpdate(HashMap<String, LinkedList<EdgeRep>> varInfoCaller, HashMap<String, LinkedList<EdgeRep>> varInfoCallee, 
    		HashMap<String, HashMap<String, HashSet<String>>> setDetailsCaller, HashMap<String, HashMap<String, HashSet<String>>> setDetailsCallee, 
    		String callerObjName, String calleeObjName) {
    	
    	HashMap<String, HashSet<String>> rsCaller = setDetailsCaller.get("RS");
    	HashMap<String, HashSet<String>> rsCallee = setDetailsCallee.get("RS");
    	HashMap<String, HashSet<String>> crsCallee = setDetailsCallee.get("CRS");
    	
    	if (rsCaller != null && rsCallee != null && rsCallee.get(calleeObjName) != null && rsCaller.get(callerObjName) != null) {
	    	rsCaller.get(callerObjName).addAll(rsCallee.get(calleeObjName));
	    	rsCaller.get(callerObjName).addAll(crsCallee.get(calleeObjName));
	    	
	    	LinkedList<EdgeRep> ller = varInfoCaller.get(callerObjName);
	    	if (ller != null) {
	    		Iterator it = ller.iterator();
	    		while (it.hasNext()) {
	    			EdgeRep er = (EdgeRep)it.next();
	    			if (!er.fieldName.equalsIgnoreCase("") && !er.desName.equalsIgnoreCase("Obj-null")) {
	    				LinkedList<EdgeRep> llerCallee = varInfoCallee.get(calleeObjName);
	    				if (llerCallee != null) {
	    					Iterator it1 = llerCallee.iterator();
	    					while (it1.hasNext()) {
	    						EdgeRep er1 = (EdgeRep)it1.next();
	    						if (er1.fieldName.equalsIgnoreCase(er.fieldName) && !er1.desName.equalsIgnoreCase("Obj-null")) {
	    							fnCallSetUpdate(varInfoCaller, varInfoCallee, setDetailsCaller, setDetailsCallee, er.desName, er1.desName);
	    						}
	    					}
	    				}
	    			}
	    		}
	    	}
    	}
    }
    
    /* Nobita code */
    //for the inter-procedure analysis
    public void fnCallGraphUpdate(HashMap<String, LinkedList<EdgeRep>> varInfoCaller, HashMap<String, LinkedList<EdgeRep>> varInfoCallerCopy,
    		HashMap<String, LinkedList<EdgeRep>> varInfoCallee, HashMap<String, ObjNode> objDetails, String callerObjName, String calleeObjName, 
    		int lineNo, HashMap<String, HashMap<String, HashSet<String>>> setDetailsCaller, boolean recursive) {
    	
    	LinkedList<EdgeRep> llerCaller = varInfoCaller.get(callerObjName);
    	if (llerCaller != null) {
    		Iterator it = llerCaller.iterator();
    		while (it.hasNext()) {
    			EdgeRep er = (EdgeRep)it.next();
    			if (!er.fieldName.equalsIgnoreCase("")) {
    				LinkedList<EdgeRep> llerCallee = varInfoCallee.get(calleeObjName);
    				if (llerCallee != null) {
    					Iterator it1 = llerCallee.iterator();
    					while (it1.hasNext()) {
    						EdgeRep er1 = (EdgeRep)it1.next();
    						if (er.fieldName.equalsIgnoreCase(er1.fieldName)) {
	    						if (er1.copyFlag) {
	    							fnCallGraphUpdateTrue(varInfoCaller, varInfoCallerCopy, varInfoCallee, objDetails, er.desName, er1.desName, lineNo, setDetailsCaller, recursive);
	    						}
	    						else {
	    							fnCallGraphUpdateFalse(varInfoCaller, varInfoCallerCopy, varInfoCallee, objDetails, callerObjName, er1.desName, lineNo, er.fieldName, setDetailsCaller, recursive);
	    						}
    						}
    					}
    				}
    			}
    		}
    	}
    	
    }
    
    /* Nobita code */
    //for the inter-procedure analysis + True is based on er1.copyFlag
    public void fnCallGraphUpdateTrue(HashMap<String, LinkedList<EdgeRep>> varInfoCaller, HashMap<String, LinkedList<EdgeRep>> varInfoCallerCopy,
    		HashMap<String, LinkedList<EdgeRep>> varInfoCallee, HashMap<String, ObjNode> objDetails, String callerObjName, String calleeObjName, 
    		int lineNo, HashMap<String, HashMap<String, HashSet<String>>> setDetailsCaller, boolean recursive) {
    	LinkedList<EdgeRep> llerCaller = varInfoCaller.get(callerObjName);
    	if (llerCaller != null) {
    		Iterator it = llerCaller.iterator();
    		while (it.hasNext()) {
    			EdgeRep er = (EdgeRep)it.next();
    			if (!er.fieldName.equalsIgnoreCase("")) {
    				LinkedList<EdgeRep> llerCallee = varInfoCallee.get(calleeObjName);
    				if (llerCallee != null) {
    					Iterator it1 = llerCallee.iterator();
    					while (it1.hasNext()) {
    						EdgeRep er1 = (EdgeRep)it1.next();
    						
    						if (er.fieldName.equalsIgnoreCase(er1.fieldName)) {
	    						if (er1.copyFlag ) {
	    							fnCallGraphUpdateTrue(varInfoCaller, varInfoCallerCopy, varInfoCallee, objDetails, er.desName, er1.desName, lineNo, setDetailsCaller, recursive);
	    						}
	    						else {
	    							fnCallGraphUpdateFalse(varInfoCaller, varInfoCallerCopy, varInfoCallee, objDetails, callerObjName, er1.desName, lineNo, er.fieldName, setDetailsCaller, recursive);
	    						}
    						}
    					}
    				}
    			}
    		}
    	}
    }
    
    
    /* Nobita code */
    //for the inter-procedure analysis + false is based on er1.copyFlag
    public void fnCallGraphUpdateFalse(HashMap<String, LinkedList<EdgeRep>> varInfoCaller, HashMap<String, LinkedList<EdgeRep>> varInfoCallerCopy,
    		HashMap<String, LinkedList<EdgeRep>> varInfoCallee, HashMap<String, ObjNode> objDetails, String callerObjName, String calleeObjName, 
    		int lineNo, String fieldName, HashMap<String, HashMap<String, HashSet<String>>> setDetails, boolean recursive) {
    	if (!calleeObjName.equalsIgnoreCase("Obj-null")) {
    		String newObjName = calleeObjName;
    		if (recursive) {
    			if (!objDetails.containsKey(calleeObjName)) {
    				newObjName = calleeObjName;
    			}
    		}
    		else {
    			newObjName = calleeObjName+"fn"+lineNo;
    		}
	    	if (!objDetails.containsKey(newObjName)) {
	    		ObjNode on = objDetails.get(callerObjName);
	    		
	    		String ObjType = on.objType;
	    		LinkedList<ClassInfo> llci = classDetails.get(ObjType);
	    		String fieldObjType = "";
	    		if (llci != null) {
	    			Iterator it = llci.iterator();
	    			while (it.hasNext()) {
	    				ClassInfo ci = (ClassInfo)it.next();
	    				if (ci.classifier.equalsIgnoreCase("field") && ci.name.equalsIgnoreCase(fieldName)) {
	    					fieldObjType = ci.type;
	    					break;
	    				}
	    			}
	    			
	    			ObjNode newObj = new ObjNode(newObjName, fieldObjType);
	    			objDetails.put(newObjName, newObj);
	    			
	    			if(!varInfoCallerCopy.containsKey(newObjName)) {
	    				varInfoCallerCopy.put(newObjName, new LinkedList<EdgeRep>());
	    			}
	    			
	    			LinkedList<EdgeRep> ller = varInfoCallerCopy.get(callerObjName);
	    			String edgeName = "";
	    			if (ller != null) {
	    				Iterator it4 = ller.iterator();
	    				while (it4.hasNext()) {
	    					EdgeRep er2 = (EdgeRep)it4.next();
	    					if (er2.desName.equalsIgnoreCase("Obj-null") && er2.fieldName.equalsIgnoreCase(fieldName) && !er2.edgeName.equalsIgnoreCase("")) {
	    						edgeName = er2.edgeName;
	    						break;
	    					}
	    				}
	    			}
	    			EdgeRep er = new EdgeRep("F", newObjName, fieldName);
	    			if (edgeName.equalsIgnoreCase("")) {
	    				edgeNumber++;
	    				edgeName = "zx"+edgeNumber;
	    				er.edgeName = edgeName;
	    				if (ller != null) {
		    				Iterator it4 = ller.iterator();
		    				while (it4.hasNext()) {
		    					EdgeRep er2 = (EdgeRep)it4.next();
		    					if (er2.desName.equalsIgnoreCase("Obj-null") && er2.fieldName.equalsIgnoreCase(fieldName) && !er2.edgeName.equalsIgnoreCase("")) {
		    						er2.edgeName = edgeName;
		    					}
		    				}
		    			}
	    			}
	    			else {
	    				er.edgeName = edgeName;
	    			}
	    			ller.add(er);
	    			
	    			//the sets
	    			String objName = newObjName;
	    			HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
					if (readSet != null && !readSet.containsKey(objName)) {
						readSet.put(objName, new HashSet<String>());
						//update modifier boolean
					}
            		HashMap<String, HashSet<String>> cumReadSet = setDetails.get("CRS");
            		if (cumReadSet != null && !cumReadSet.containsKey(objName)) {
            			cumReadSet.put(objName, new HashSet<String>());
            			//update modifier boolean
            		}
            		HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
            		if (writeSet != null && !writeSet.containsKey(objName)) {
            			writeSet.put(objName, new HashSet<String>());
            			//update modifier boolean
            		}
            		HashMap<String, HashSet<String>> mWriteSet = setDetails.get("MWS");
            		if (mWriteSet != null && !mWriteSet.containsKey(objName)) {
            			mWriteSet.put(objName, new HashSet<String>());
            			//update modifier boolean
            		}
            		HashMap<String, HashSet<String>> cumWriteSet = setDetails.get("CWS");
            		if (cumWriteSet != null && !cumWriteSet.containsKey(objName)) {
            			cumWriteSet.put(objName, new HashSet<String>());
            			//update modifier boolean
            		}
	    			
	    			
	    			LinkedList<EdgeRep> llerCallee = varInfoCallee.get(calleeObjName);
	    			if (llerCallee != null) {
	    				Iterator it1 = llerCallee.iterator();
	    				while (it1.hasNext()) {
	    					EdgeRep er1 = (EdgeRep)it1.next();
	    					if (!er1.fieldName.equalsIgnoreCase("")) {
	    						fnCallGraphUpdateFalse(varInfoCaller, varInfoCallerCopy, varInfoCallee, objDetails, newObjName, er1.desName, lineNo, er1.fieldName, setDetails, recursive);
	    					}
	    				}
	    			}
	    			
	    		}
	    	}
	    	else {
	    		//to be added for the recursive part
	    		//if (recursive) {
	    			LinkedList<EdgeRep> llerCaller = varInfoCallerCopy.get(newObjName);
	    			if(llerCaller != null) {
	    				Iterator it = llerCaller.iterator();
	    				boolean found = false;
	    				while (it.hasNext()) {
	    					EdgeRep er = (EdgeRep)it.next();
	    					if (er.desName.equalsIgnoreCase(calleeObjName) && er.fieldName.equalsIgnoreCase(fieldName)) {
	    						found = true;
	    						break;
	    					}
	    				}
	    				
	    				if (!found) {
	    					EdgeRep er = new EdgeRep("F", newObjName, fieldName);
	    					edgeNumber++;
		    				String edgeName = "zx"+edgeNumber;
		    				er.edgeName = edgeName;
		    				llerCaller.add(er);
	    				}
	    				
	    				//again a function call
	    			}
	    		//}
	    	}
    	} 
    	else {
    		LinkedList<EdgeRep> ller = varInfoCallerCopy.get(callerObjName);
    		String edgeName = "";
    		boolean found = false;
			if (ller != null) {
				Iterator it4 = ller.iterator();
				while (it4.hasNext()) {
					EdgeRep er2 = (EdgeRep)it4.next();
					if (er2.desName.equalsIgnoreCase("Obj-null") && er2.fieldName.equalsIgnoreCase(fieldName) && !er2.edgeName.equalsIgnoreCase("")) {
						edgeName = er2.edgeName;
						found = true;
						break;
					}
					if (er2.desName.equalsIgnoreCase("Obj-null") && er2.fieldName.equalsIgnoreCase(fieldName)) {
						found = true;
					}
				}
			
				if (!found) {
					EdgeRep er = new EdgeRep("F", "Obj-null", fieldName);
					if (edgeName.equalsIgnoreCase("")) {
						edgeNumber++;
						edgeName = "zx"+edgeNumber;
						if (ller != null) {
		    				Iterator it5 = ller.iterator();
		    				while (it5.hasNext()) {
		    					EdgeRep er2 = (EdgeRep)it5.next();
		    					if (er2.desName.equalsIgnoreCase("Obj-null") && er2.fieldName.equalsIgnoreCase(fieldName) && !er2.edgeName.equalsIgnoreCase("")) {
		    						er2.edgeName = edgeName;
		    					}
		    				}
		    			}
					}
					ller.add(er);
				}
			}
    	}
    }
    
    /* Nobita code */
    //for the inter-procedure analysis
    public void conCallGraphUpdate(HashMap<String, LinkedList<EdgeRep>> varInfoCaller, HashMap<String, LinkedList<EdgeRep>> varInfoCallee,
    		HashMap<String, ObjNode> objDetails, String callerObjName, String calleeObjName, int lineNo,
    		HashMap<String, HashMap<String, HashSet<String>>> setDetails) {
    	
    	LinkedList<EdgeRep> llerCaller = varInfoCaller.get(callerObjName);
    	LinkedList<EdgeRep> llerCallee = varInfoCallee.get(calleeObjName);
    	if (llerCallee != null && llerCaller != null) {
    		Iterator it = llerCallee.iterator();
    		while (it.hasNext()) {
    			EdgeRep er  = (EdgeRep)it.next();
    			if (!er.fieldName.equalsIgnoreCase("")) 
    			{
    				if(er.desName.equalsIgnoreCase("Obj-null")) {
    					
    					boolean found = false;
    					Iterator it1 = llerCaller.iterator();
    					while (it1.hasNext()) {
    						EdgeRep er2 = (EdgeRep)it1.next();
    						if (er2.desName.equalsIgnoreCase(er.desName) && er2.fieldName.equalsIgnoreCase(er.fieldName)) {
    							found = true;
    							break;
    						}
    					}
    					
    					if (!found) {
	    					EdgeRep er1 = new EdgeRep("F", er.desName, er.fieldName);
	    					er1.edgeName = er.edgeName;
	    					llerCaller.add(er1);
    					}
    				}
    				else {
    					
    					//get object details
    					ObjNode on = objDetails.get(callerObjName);
    					
    					String ObjType = on.objType;
    					LinkedList<ClassInfo> llci = classDetails.get(ObjType);
    		    		String fieldObjType = "";
    		    		if (llci != null) {
    		    			Iterator it1 = llci.iterator();
    		    			while (it.hasNext()) {
    		    				ClassInfo ci = (ClassInfo)it1.next();
    		    				if (ci.classifier.equalsIgnoreCase("field") && ci.name.equalsIgnoreCase(er.fieldName)) {
    		    					fieldObjType = ci.type;
    		    					break;
    		    				}
    		    			}
    		    			
    		    			String newObjName = er.desName+"-c"+lineNo;
    		    			
    		    			//fixing object
    		    			ObjNode newObj = new ObjNode(newObjName, fieldObjType);
    		    			objDetails.put(newObjName, newObj);
    					
    		    			//fixing the graph
	    					EdgeRep er1 = new EdgeRep("F", newObjName, er.fieldName);
	    					er1.edgeName = er.edgeName;
	    					llerCaller.add(er1);
	    					
	    					//fixing the sets
	    					String objName = newObjName;
	    	    			HashMap<String, HashSet<String>> readSet = setDetails.get("RS");
	    					if (readSet != null && !readSet.containsKey(objName)) {
	    						readSet.put(objName, new HashSet<String>());
	    						//update modifier boolean
	    					}
	                		HashMap<String, HashSet<String>> cumReadSet = setDetails.get("CRS");
	                		if (cumReadSet != null && !cumReadSet.containsKey(objName)) {
	                			cumReadSet.put(objName, new HashSet<String>());
	                			//update modifier boolean
	                		}
	                		HashMap<String, HashSet<String>> writeSet = setDetails.get("WS");
	                		if (writeSet != null && !writeSet.containsKey(objName)) {
	                			writeSet.put(objName, new HashSet<String>());
	                			//update modifier boolean
	                		}
	                		HashMap<String, HashSet<String>> mWriteSet = setDetails.get("MWS");
	                		if (mWriteSet != null && !mWriteSet.containsKey(objName)) {
	                			mWriteSet.put(objName, new HashSet<String>());
	                			//update modifier boolean
	                		}
	                		HashMap<String, HashSet<String>> cumWriteSet = setDetails.get("CWS");
	                		if (cumWriteSet != null && !cumWriteSet.containsKey(objName)) {
	                			cumWriteSet.put(objName, new HashSet<String>());
	                			//update modifier boolean
	                		}
	    		    		
	    		    		//call the recursive function here
	                		if(!varInfoCaller.containsKey(newObjName)) {
	                			varInfoCaller.put(newObjName, new LinkedList<EdgeRep>());
	                		}
	    		    		conCallGraphUpdate(varInfoCaller, varInfoCallee, objDetails, newObjName, er.desName, lineNo, setDetails);
    		    		}
    				}
    			}
    		}
    	}
    }
    
} // end of X10PrettyPrinterVisitor
