import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VaporProgram;
import cs132.vapor.ast.VBuiltIn.Op;

import cs132.util.*;
import cs132.vapor.ast.*;
import cs132.vapor.parser.*;

import java.io.*;
import java.util.*;


import Graph.*;

/*
    see build script
    File has to be compiled using class path to include vapor-parser.jar and this folder
    CLASSPATH=$VAPOR_P:/p3
    javac -classpath ${CLASSPATH} V2VM.java
*/

class V2VM
{
    public static String tmp_struct = null;
    public static void main(String[] args)
        throws IOException, Throwable
    {
        InputStream in = System.in;
        PrintStream err = System.err;
        VaporProgram prog = parseVapor(in,err);  // should be the tree

        // figure out how to traverse the vapor program tree?
        // for(VDataSegment i: prog.dataSegments) {
        //     System.out.println(i.getClass() + " " + i.sourcePos);
        //     for(VOperand j: i.values) {
        //         System.out.println("    " + j.getClass() + " " + j.sourcePos + " " + j);
        //     }
        // }

        // TESTS
        // VisitorPrinter v_instr_visitor = new VisitorPrinter();
        // VisitorData v_data_visitor = new VisitorData();
        // graph_tester();
        // info_printer(prog,v_instr_visitor);
        // data_grab(prog,v_data_visitor);

        VisitorVaporM vapor_m_visitor = new VisitorVaporM();
        Vector<VisitorGraphBuilder> all_graphs = infoo_printer(prog);


        //PRINT ALL THE FLOW_GRAPHS:

        // graph_tester();
        // for(VisitorGraphBuilder x: all_graphs) {
        //     for(GNode i: x.flow_graph.nodes()) {
        //         Vector<String> def_temp = (Vector<String>)x.def.get(i.node_id);
        //         Vector<String> use_temp = (Vector<String>)x.use.get(i.node_id);
        //         System.out.println("Inside Node " + i.node_id + " line: " + i.line.line);
        //         System.out.println();
        //         System.out.println("    defs:");
        //         if(def_temp != null){
        //             for(String j: def_temp) {
        //                 System.out.println("        " + j);
        //             }
        //         }
        //         System.out.println();
        //         System.out.println("    uses:");
        //         if(use_temp != null) {
        //             for(String j: use_temp) {
        //                 System.out.println("        " +j);
        //             }
        //         }
        //         System.out.println();
        //     }
        // }


        //BUILD THE INTERVALS FROM THE FLOW GRAPH
        Vector<Vector<Pair>> interval_vector = new Vector<>();
        // Vector<Map<String, Interval>> interval_vec = new Vector<>();

        //BUILD INTERVAL
        for(VisitorGraphBuilder i: all_graphs) {
            Vector<Pair> tmp_pair_vec = build_intervals(i.flow_graph.nodes(), i.def, i.use);
            interval_vector.add(tmp_pair_vec);
        }

        Vector<Vector<Loop_pair>> all_loops = new Vector<>();
        for(VFunction f: prog.functions) {
            Vector<Loop_pair> tmp1 = check_loops(f);
            all_loops.add(tmp1);
        }

        // for(Vector<Pair> j: interval_vector) {
        //     System.out.println("new function");
        //     System.out.println(interval_vector.size());
        //     for(Pair n: j) {
        //         System.out.println("    " + n.key + " is live from line: " + n.value.start + " to line: " + n.value.end);
        //     }
        // }

        //COMMENT THIS OUT TO UNDO THE INTERVAL PATCHING
        VFunction[] funcs = prog.functions;

        for(int j = 0; j < interval_vector.size(); j++) {
            VFunction ftemp = funcs[j];
            for(int k = 0; k < interval_vector.get(j).size(); k++) {
                Pair curr_pair = interval_vector.get(j).get(k);

                for(VVarRef.Local arg_tmp: ftemp.params) {
                    if(curr_pair.key.equals(arg_tmp.ident.toString())) {
                        // System.out.println(arg_tmp.ident + " " + curr_pair.key + " " + ftemp.sourcePos.line);
                        curr_pair.value.start = ftemp.sourcePos.line;
                    }
                }
                
                if(all_loops.get(j).size() > 0) {
                    for(int l = 0; l < all_loops.get(j).size(); l++) {
                        Loop_pair curr_loop = all_loops.get(j).get(l);

                        if(curr_pair.value.start <= curr_loop.loop_start_line && curr_pair.value.end >= curr_loop.loop_start_line && curr_pair.value.end <= curr_loop.loop_end_line) {
                            // System.out.println("Need to patch " + curr_pair.key);
                            curr_pair.value.end = curr_loop.loop_end_line;

                        }
                    }
                }

                interval_vector.get(j).set(k, curr_pair);
            }
        }

        //BUILD INTERVAL MAP BUILD
        // for(VisitorGraphBuilder i: all_graphs) {
        //     Map<String, Interval> tmp_pair_vec = build_intervals(i.flow_graph.nodes(), i.def, i.use);
        //     for(String xy: tmp_pair_vec.keySet()) {
        //         System.out.println("outside function " + xy);
        //     }
        //     interval_vec.add(tmp_pair_vec);
        // }
        

        // PRINT ALL THE INTERVALS
        // for(Vector<Pair> j: interval_vector) {
        //     System.out.println("new function");
        //     System.out.println(interval_vector.size());
        //     for(Pair n: j) {
        //         System.out.println("    " + n.key + " is live from line: " + n.value.start + " to line: " + n.value.end);
        //     }
        // }

        //BUILD INTERVAL MAP PRINT
        // for(Map<String, Interval> j: interval_vec) {
        //     System.out.println("new function");
        //     System.out.println(interval_vec.size());
        //     for(String n: j.keySet()) {
        //         System.out.println("    " + n + " is live from line: " + j.get(n).start.line + " to line: " + j.get(n).end.line);
        //     }
        // }

        Vector<LinearScan> scans = new Vector<>();
        // System.out.println(interval_vector.size() + " " + all_graphs.size());
        // System.out.println();
        for(int i = 0; i < all_graphs.size(); i++) {
            // System.out.println("new function");
            LinearScan ll = new LinearScan();
            ll.LinearScanRegisterAllocation(interval_vector.get(i), all_graphs.get(i));
            scans.add(ll);
        }

        vaporm_grab(prog,vapor_m_visitor, scans);

        // for(LinearScan lscan: scans) {
        //     System.out.println("New Function");
        //     lscan.print_reg_map();
        //     lscan.print_local_map();
        //     System.out.println(lscan.local_list.size());
        // }

        return;
    }
    //----------------------------------------
    public static Vector<Loop_pair> check_loops(VFunction func) {

        Vector<Loop_pair> ret = new Vector<>();
        VCodeLabel [] lbl = func.labels;
        Vector<VCodeLabel> while_labels = new Vector<>();

        for(int i = 0; i < lbl.length; i++) {
            VCodeLabel l = lbl[i];
            String lbl_string = l.ident.toString();
            if(lbl_string.indexOf("while") != -1) {

                while_labels.add(l);
            }
        }

        for(int i = 0; i < while_labels.size(); i++) {
            String substr = while_labels.get(i).ident.toString().substring(0, while_labels.get(i).ident.toString().indexOf("_"));
            Loop_pair t1 = new Loop_pair(substr, while_labels.get(i).sourcePos.line);

            for(int j = i + 1; j < while_labels.size(); j++) {
                String substr2 = while_labels.get(j).ident.toString().substring(0, while_labels.get(j).ident.toString().indexOf("_"));
                if(substr.equals(substr2) && i != j) {
                    // System.out.println(substr);
                    t1.loop_end_line = while_labels.get(j).sourcePos.line;
                    while_labels.remove(j);
                    break;
                }
            }
            ret.add(t1);
        }

        for(Loop_pair tmp: ret) {
            // System.out.println("While loop " + tmp.loop_name + " spans from line " + tmp.loop_start_line + " to " + tmp.loop_end_line);
        }

        return ret;
    }

    public static Vector<Pair> build_intervals(Vector<GNode> g, Map<Integer, Vector<String>> d, Map<Integer, Vector<String>> u) {

        Map<String, Interval> intervals = new HashMap<>();

        for(GNode i: g) {
            Vector<String> def_temp = (Vector<String>)d.get(i.node_id);
            Vector<String> use_temp = (Vector<String>)u.get(i.node_id);
            // System.out.println("Inside Node " + i.node_id + " line: " + i.line.line);
            // System.out.println();
            // System.out.println("    defs:");

            if(def_temp != null){
                for(String j: def_temp) {
                    int cont = 0;

                    if(intervals.containsKey(j)) {
                        Interval change = intervals.get(j);
                        change.end = i.line.line;
                        Interval check = intervals.replace(j, change);
                        if(check == null) {
                            System.out.println("ERROR AT NODE " + j);
                        }
                        else {
                            // System.out.println("        ~~ " + j + " " + change.start.line);
                        }
                        cont = 1;
                    }
                    else {
                        Interval new_var = new Interval(j, i.line.line);;
                        intervals.put(j, new_var);
                        new_var.end = i.line.line;
                        // System.out.println("        ~ " + j + " " + new_var.start.line);
                        cont = 2;
                    }
                    // System.out.println("        def " + j + " " + intervals.get(j).start.line + " " + cont);
                }
            }

            // System.out.println();
            // System.out.println("    uses:");

            if(use_temp != null) {
                for(String j: use_temp) {
                    int cont = 0;

                    if(intervals.containsKey(j)) {
                        Interval change = intervals.get(j);
                        change.end = i.line.line;
                        Interval check = intervals.replace(j, change);
                        if(check == null) {
                            System.out.println("ERROR AT NODE " + j);
                        }
                        else{
                            // System.out.println("        ~~ " + j + " " + change.start.line);
                        }
                        cont = 1;
                    }
                    else {
                        Interval new_var = new Interval(j, i.line.line);
                        new_var.end = i.line.line;
                        intervals.put(j, new_var);
                        // System.out.println("        ~ " + j + " " +new_var.start.line);
                        cont = 2;
                    }

                    // System.out.println("        use " + j + " " + intervals.get(j).start.line + " " + cont);
                }
            }
            // for(String n: intervals.keySet()) {
            //     System.out.println("in func " + n + " " + intervals.get(n).start.line + " ");
            // }
            // System.out.println();


        }
        // if(intervals.containsKey("t.6")) {
        //     System.out.println("~t.6 goes from " + intervals.get("t.6").start.line + " to " + intervals.get("t.6").end.line);
        // }
        for(String n: intervals.keySet()) {
            // System.out.println("in func " + n + " from " );
        }
        // System.out.println("gnode size is " + g.size());
        Vector<Pair> interval_vec = new Vector<>();

        for(String i: intervals.keySet()) {
            // System.out.println("in vector add, key is " + i+ " from " + intervals.get(i).start.line + " to " + intervals.get(i).end.line);
            Interval tmp1 = intervals.get(i);
            Pair tmp2 = new Pair(i, tmp1);

            interval_vec.add(tmp2);
        }

        for(int i = 0; i < interval_vec.size() - 1; i++) {
            int min = i;
            for(int j = i + 1; j < interval_vec.size(); j++) {
                if(interval_vec.get(j).value.start < interval_vec.get(min).value.start) {
                    min = j;
                }
            }
            Pair tmp3 = interval_vec.get(min);
            interval_vec.setElementAt(interval_vec.get(i), min);
            interval_vec.setElementAt(tmp3, i);
        }

        return interval_vec;
        // return intervals;
    }

    public static void graph_tester() {
        System.out.println("====================== Testing GRAPH ====================== Testing GRAPH ====================== Testing GRAPH ======================");
        Graph g = new Graph();
        GNode n_1 = g.new_node();
        g.show();
        GNode n_2 = g.new_node();
        g.show();
        boolean worked = g.add_edge(n_1, n_2);
        System.out.println("~~add edge from 1 and 2~~");
        // g.show();

        // if(n_1.goes_to(n_2)) {
        //     System.out.println("supposed to be true 1");
        // }
        // else if(n_1.comes_from(n_2)) {
        //     System.out.println("not supposed to be true 2");
        // }

        // if(n_2.goes_to(n_1)) {
        //     System.out.println("not supposed to be true");
        // }
        // else if(n_2.comes_from(n_1)) {
        //     System.out.println("supposed to be true 2");
        // }

        for(GNode j: g.nodes()) {
            System.out.println("Inside " + j.node_id);
            for(GNode k: j.succ) {
                System.out.println("    Head: " + g.head.node_id + ", " + j.node_id + "'s successor is " + k.node_id);
            }
        }

        GNode n_3 = g.new_node();
        worked = g.add_edge(n_2, n_3);
        System.out.println("~~add edge from 2 to 3~~");

        for(GNode j: g.nodes()) {
            System.out.println("Inside " + j.node_id);
            for(GNode k: j.succ) {
                System.out.println("    Head: " + g.head.node_id + ", " + j.node_id + "'s successor is " + k.node_id);
            }
        }
        // g.show();

        worked = g.rm_edge(n_1, n_2);
        System.out.println("~~rm edge between 1 and 2~~");
        for(GNode j: g.nodes()) {
            System.out.println("Inside " + j.node_id);
            for(GNode k: j.succ) {
                System.out.println("    Head: " + g.head.node_id + ", " + j.node_id + "'s successor is " + k.node_id);
            }
        }
        // g.show();

        System.out.println("~~add edge to 1 and 3~~");
        worked = g.add_edge(n_1, n_3);
        for(GNode j: g.nodes()) {
            System.out.println("Inside " + j.node_id);
            for(GNode k: j.succ) {
                System.out.println("    Head: " + g.head.node_id + ", " + j.node_id + "'s successor is " + k.node_id);
            }
        }
        g.show();

        worked = g.add_edge(n_2, n_1);
        System.out.println("~~add edge to 2 and 1~~");
        for(GNode j: g.nodes()) {
            System.out.println("Inside " + j.node_id);
            for(GNode k: j.succ) {
                System.out.println("    Head: " + g.head.node_id + ", " + j.node_id + "'s successor is " + k.node_id);
            }
        }
        g.show();

        GNode n_4 = g.new_node();
        worked = g.add_edge(n_4, n_2);
        System.out.println("~~add edge to 4 and 2~~");
        for(GNode j: g.nodes()) {
            System.out.println("Inside " + j.node_id);
            for(GNode k: j.succ) {
                System.out.println("    Head: " + g.head.node_id + ", " + j.node_id + "'s successor is " + k.node_id);
            }
        }
        g.show();

        worked = g.add_edge(n_1, n_4);
        System.out.println("~~add edge to 1 and 4~~");
        for(GNode j: g.nodes()) {
            System.out.println("Inside " + j.node_id);
            for(GNode k: j.succ) {
                System.out.println("    Head: " + g.head.node_id + ", " + j.node_id + "'s successor is " + k.node_id);
            }
        }
        g.show();

    }

    public static VaporProgram parseVapor(InputStream in, PrintStream err)
        throws IOException
    {
        VaporProgram tree;
        boolean allowLocals = true;
        boolean allowStack = false;
        String[] registers = null;
        Op[] ops = {
            Op.Add, Op.Sub, Op.MulS, Op.Eq, Op.Lt, Op.LtS,
            Op.PrintIntS, Op.HeapAllocZ, Op.Error,
        };

        try
        {
            tree = VaporParser.run(new InputStreamReader(in), 1, 1,
                                    java.util.Arrays.asList(ops),
                                    allowLocals, registers, allowStack);
        }
        catch (ProblemException ex)
        {
            err.println(ex.getMessage());
            return null;
        }

        return tree;
    }
    //----------------------------------------

    public static void go_visit(VInstr.Visitor v,VInstr vi)
        throws Throwable
    {
        String classy = vi.getClass().toString();
        if(classy.equals("class cs132.vapor.ast.VAssign"))
        {
            v.visit((cs132.vapor.ast.VAssign)vi);
        }
        else if(classy.equals("class cs132.vapor.ast.VBranch"))
        {
            v.visit((cs132.vapor.ast.VBranch)vi);
        }
        else if(classy.equals("class cs132.vapor.ast.VBuiltIn"))
        {
            v.visit((cs132.vapor.ast.VBuiltIn)vi);
        }
        else if(classy.equals("class cs132.vapor.ast.VCall"))
        {
            v.visit((cs132.vapor.ast.VCall)vi);
        }
        else if(classy.equals("class cs132.vapor.ast.VGoto"))
        {
            v.visit((cs132.vapor.ast.VGoto)vi);
        }
        else if(classy.equals("class cs132.vapor.ast.VMemRead"))
        {
            v.visit((cs132.vapor.ast.VMemRead)vi);
        }
        else if(classy.equals("class cs132.vapor.ast.VMemWrite"))
        {
            v.visit((cs132.vapor.ast.VMemWrite)vi);
        }
        else if(classy.equals("class cs132.vapor.ast.VReturn"))
        {
            v.visit((cs132.vapor.ast.VReturn)vi);
        }
        else
        {
            System.out.println("error: '" + vi.getClass().toString() + "'");
        }
    }

    public static void goat_visit(VisitorGraphBuilder v,VInstr vi)
        throws Throwable
    {
        String classy = vi.getClass().toString();
        // System.out.println("\t" + classy);
        if(classy.equals("class cs132.vapor.ast.VAssign"))
        {
            String ret_val = v.graph_visit((cs132.vapor.ast.VAssign)vi, tmp_struct);
            tmp_struct = ret_val;
        }
        else if(classy.equals("class cs132.vapor.ast.VBranch"))
        {
            String ret_val = v.graph_visit((cs132.vapor.ast.VBranch)vi, tmp_struct);
            tmp_struct = ret_val;
        }
        else if(classy.equals("class cs132.vapor.ast.VBuiltIn"))
        {
            String ret_val = v.graph_visit((cs132.vapor.ast.VBuiltIn)vi, tmp_struct);
            tmp_struct = ret_val;
        }
        else if(classy.equals("class cs132.vapor.ast.VCall"))
        {
            String ret_val = v.graph_visit((cs132.vapor.ast.VCall)vi, tmp_struct);
            tmp_struct = ret_val;
        }
        else if(classy.equals("class cs132.vapor.ast.VGoto"))
        {
            String ret_val = v.graph_visit((cs132.vapor.ast.VGoto)vi, tmp_struct);
            tmp_struct = ret_val;
        }
        else if(classy.equals("class cs132.vapor.ast.VMemRead"))
        {
            String ret_val = v.graph_visit((cs132.vapor.ast.VMemRead)vi, tmp_struct);
            tmp_struct = ret_val;
        }
        else if(classy.equals("class cs132.vapor.ast.VMemWrite"))
        {
            String ret_val = v.graph_visit((cs132.vapor.ast.VMemWrite)vi, tmp_struct);
            tmp_struct = ret_val;
        }
        else if(classy.equals("class cs132.vapor.ast.VReturn"))
        {
            String ret_val = v.graph_visit((cs132.vapor.ast.VReturn)vi, tmp_struct);
            tmp_struct = ret_val;
        }
        else
        {
            System.out.println("error: '" + vi.getClass().toString() + "'");
        }
    }
    //----------------------------------------

    // VAPOR-M CODE GENERATOR
    public static void data_grab(VaporProgram  prog, VisitorData vdatav)
        throws Throwable
    {
        VFunction[] fns = prog.functions;       // All the functions in this program
        VDataSegment[] dat = prog.dataSegments; // All the data segments in this program
        String[] reg = prog.registers;          // registers allowed to use

        HashMap<String,ArrayList<String>> vtabs = new HashMap<String,ArrayList<String>>();

        // DATA SEGMENT
        for (VDataSegment d : dat)
        {
            VOperand.Static[] vals = d.values;
            ArrayList<String> arlst = new ArrayList<String>();
            for(VOperand v : vals) // add to the map
            {
                arlst.add(v.toString());
            }
            if(!arlst.isEmpty())
            {
                vtabs.put(d.ident,arlst);
            }
        }

        // print vtable
        for (Map.Entry<String,ArrayList<String>> entry : vtabs.entrySet())
        {
            System.out.println("const " + entry.getKey());
            ArrayList<String> l = entry.getValue();
            for( String s : l )
            {
                System.out.println("  " + s);
            }
            System.out.println("");
        }

        // FUNCTION: Vfunction
        for (VFunction f : fns)
        {
            String tab = "\t";
            VVarRef.Local[] pms = f.params;   // function parameters
            String[] vs = f.vars;             // function local variables
            VCodeLabel[] lbl = f.labels;      // code labels
            VInstr[] bdy = f.body;            // instructions

            ArrayList<String> diflst = new ArrayList<String>();
            ArrayList<String> loclst = new ArrayList<String>();

            HashMap<Integer,String> lmap = new HashMap<Integer,String>();
            HashMap<String,String> v2reg = new HashMap<String,String>();

            // System.out.println(f.ident);
            // System.out.println("Params: ");
            for (VVarRef.Local p : pms)
            {
                // System.out.println(tab + p.ident);
                diflst.add(p.ident);
            }
            // System.out.println("Locals:");
            for (String v : vs)
            {
                // System.out.println(tab + v);
                loclst.add(v);
                v2reg.put(v, "");
            }
            // System.out.println("Labels:");
            for (VCodeLabel l : lbl)
            {
                // System.out.println(tab + "pos: " + l.sourcePos + "    " + l.ident );
                lmap.put(l.sourcePos.line, l.ident);
            }

            //jank
            vdatav.vartoreg = v2reg;

            // calculate the in/out/locals
            int in,out,local;
            int argno;
            in = 0; out = 0; local = 0;
            argno = 0;

            argno = diflst.size();
            local = loclst.size();

            System.out.println("func " + f.ident + " [in " + Integer.toString(in)   // function interface
                                + ", out " + Integer.toString(out) + ", local "
                                + Integer.toString(local) + "]");

            // if(local >= 1) // save regs on enter
            // {
            //     for(int i = 0; i < local; i++)
            //     {
            //         System.out.println("  " + "local[" + Integer.toString(i) + "] = $s" + Integer.toString(i));
            //     }
            // }

            if(argno >= 1) // get the parameters
            {
                for(int i = 0; i < argno; i++)
                {
                    System.out.println("  " + "$t" + Integer.toString(i) + " = $a" + Integer.toString(i));
                    vdatav.rnum++;  // inc reg cnt
                }
            }

            for (VInstr vi : bdy)   // visit the instructions && print labelas
            {
                String classy = vi.getClass().toString();
                if(classy.equals("class cs132.vapor.ast.VReturn"))
                {
                    VReturn vr = (VReturn)vi;   // put ret val into $v0
                    if(vr.value != null)
                    {
                        String retv = vdatav.vartoreg.get(vr.value.toString());
                        System.out.println( "  $v0 = " + retv );
                    }

                //     if(local >= 1) // restore regs before return
                //     {
                //         for(int i = 0; i < local; i++)
                //         {
                //             System.out.println("  " + "$s" + Integer.toString(i) + " = local[" + Integer.toString(i) + "]");
                //         }
                //     }
                }

                go_visit(vdatav,vi);

                int i = vi.sourcePos.line;
                // System.out.println(i);
                if(lmap.containsKey(vi.sourcePos.line+1))  // if the sourcePos + 1 = sourcePos of a label then print the label
                {
                    System.out.println(lmap.get(i+1)+":");
                }
            }

            System.out.println("");

            vdatav.rnum = 0; // reset reg count for each function
        }

        System.out.println("");
    }

    //----------------------------------------

// <<<<<<< HEAD
    public static void info_printer(VaporProgram  prog, VisitorPrinter vprinter)
        throws Throwable
    {
        VFunction[] fns = prog.functions;       // All the functions in this program
        VDataSegment[] dat = prog.dataSegments; // All the data segments in this program
        String[] reg = prog.registers;          // registers allowed to use
        String tab = "\t";

        // Data Segment
        System.out.println("Data Segments:\n");
        for (VDataSegment d : dat)
        {
            VOperand.Static[] vals = d.values;

            // VDataSegment
            System.out.println("data name: " + d.ident);
            System.out.println("data idx: " + d.index);
            System.out.println("mutable: " + d.mutable);
            for(VOperand v : vals)
            {
                System.out.println("val: " + v);
            }
            System.out.println("");
        }

        // FUNCTION: Vfunction
        System.out.println("Functions:\n");
        for (VFunction f : fns)
        {
            VVarRef.Local[] pms = f.params;   // function parameters
            String[] vs = f.vars;             // function local variables
            VCodeLabel[] lbl = f.labels;      // code labels
            VInstr[] bdy = f.body;            // instructions

            System.out.println("function name: " + f.ident);    // see VTarget
            System.out.println("function idx: " + f.index);
            System.out.println("function params:");
            for (VVarRef.Local p : pms)
            {
                System.out.println(tab + p.ident);
            }
            System.out.println("function local vars:");
            for (String v : vs)
            {
                System.out.println(tab + v);
            }
            System.out.println("function code labels:");
            for (VCodeLabel l : lbl)
            {
                System.out.println(tab + "pos: " + l.sourcePos + "    " + l.ident );
            }
            System.out.println("function instructions: (print visitor)");
            for (VInstr vi : bdy)
            {
                go_visit(vprinter,vi);
            }
            System.out.println("");
        }

        System.out.println("----------------------------------------\n");
    }
    //----------------------------------------

    public static Vector<VisitorGraphBuilder> infoo_printer(VaporProgram  prog)
        throws Throwable
    {
        VFunction[] fns = prog.functions;       // All the functions in this program
        String tab = "\t";
        Vector<VisitorGraphBuilder> ret = new Vector<>();


        for (VFunction f : fns)
        {
            VVarRef.Local[] pms = f.params;   // function parameters
            String[] vs = f.vars;             // function local variables
            VCodeLabel[] lbl = f.labels;      // code labels
            VInstr[] bdy = f.body;            // instructions
            VisitorGraphBuilder vprinter = new VisitorGraphBuilder();
            // System.out.println("function instructions: (print visitor)");
            for (VInstr vi : bdy)
            {
                goat_visit(vprinter,vi);
            }
            ret.add(vprinter);
            // System.out.println("");

        }

        return ret;
    }



    //----------------------------------------

    // VAPOR-M CODE GENERATOR
    public static void vaporm_grab(VaporProgram  prog, VisitorVaporM vdatav, Vector<LinearScan> scans)
        throws Throwable
    {
        VFunction[] fns = prog.functions;       // All the functions in this program
        VDataSegment[] dat = prog.dataSegments; // All the data segments in this program
        String[] reg = prog.registers;          // registers allowed to use

        HashMap<String,ArrayList<String>> vtabs = new HashMap<String,ArrayList<String>>();

        // DATA SEGMENT
        for (VDataSegment d : dat)
        {
            VOperand.Static[] vals = d.values;
            ArrayList<String> arlst = new ArrayList<String>();
            for(VOperand v : vals) // add to the map
            {
                arlst.add(v.toString());
            }
            if(!arlst.isEmpty())
            {
                vtabs.put(d.ident,arlst);
            }
            else{
                vtabs.put(d.ident,arlst);
            }
        }

        // print vtable
        for (Map.Entry<String,ArrayList<String>> entry : vtabs.entrySet())
        {
            System.out.println("const " + entry.getKey());
            ArrayList<String> l = entry.getValue();
            for( String s : l )
            {
                System.out.println("  " + s);
            }
            System.out.println("");
        }

        Vector<Integer> function_locals = new Vector<>();
        Vector<Integer> function_outs = new Vector<>();
        Vector<Vector<String>> save_reg = new Vector<>();

        for(int k = 0; k < fns.length; k++) {
            VFunction f = fns[k];
            VInstr[] bdy = f.body;
            int out_num = 0;
            int locals_num = 0;
            VVarRef.Local[] pms = f.params;
            int curr_func_params = pms.length;
            LinearScan curr_scan = scans.get(k);
            save_reg.add(new Vector<>());
            int last_line = bdy[bdy.length - 1].sourcePos.line;

            for(int m = 0; m < bdy.length; m++) {
                VInstr vi = bdy[m];
                String instr_class = vi.getClass().toString();
                if(instr_class.equals("class cs132.vapor.ast.VCall")) {
                    int vcall_args = ((VCall)vi).args.length;

                    if(vcall_args > 4 && vcall_args > out_num) {
                        out_num = vcall_args;
                    }

                    if(!f.ident.toString().equals("Main")) {
                        // System.out.println("function name is " + f.ident.toString());
                        int line_after = m + 1;
                        int line_before = m - 1;

                        if ((line_after != bdy.length - 1) && (line_before >= 0)) {

                            VInstr vbefore = bdy[line_before];
                            VInstr vafter = bdy[line_after];

                            Vector<String> before_reg_mappings = null;
                            Vector<String> after_reg_mappings = null;

                            boolean found = false;
                            int starting_line = vbefore.sourcePos.line;

                            while(!found) {
                                if(curr_scan.registers.containsKey(starting_line)) {
                                    before_reg_mappings = curr_scan.registers.get(starting_line);
                                    found = true;
                                }
                                else {
                                    starting_line--;
                                }

                                if(starting_line < 0) {
                                    found = true;
                                }
                            }

                            found = false;
                            starting_line = vafter.sourcePos.line;

                            while(!found) {
                                if(curr_scan.registers.containsKey(starting_line)) {
                                    after_reg_mappings = curr_scan.registers.get(starting_line);
                                    found = true;
                                }
                                else {
                                    starting_line--;
                                }

                                if(starting_line > last_line) {
                                    found = true;
                                }
                            }

                            if(before_reg_mappings != null && after_reg_mappings != null) {

                                for(int lmn = 0; lmn < before_reg_mappings.size(); lmn++) {
                                    String b1 = before_reg_mappings.get(lmn).toString();
                                    String a1 = after_reg_mappings.get(lmn).toString();

                                    if(a1.equals(b1) && a1 != "") {

                                        if(save_reg.elementAt(k).indexOf(a1.toString()) == -1) {
                                            // System.out.println("        Adding " + a1 + " at the " + k + " function");
                                            save_reg.elementAt(k).add(a1);
                                        }
                                    }
                                }

                                locals_num = save_reg.elementAt(k).size();
                            }

                        }

                    }

                }

            }

            function_outs.add(out_num);
            function_locals.add(locals_num);

        }

        // FUNCTION: Vfunction
        for (int k = 0; k < fns.length; k++)
        {
            VFunction f = fns[k];
            vdatav.all_mappings = scans.get(k);
            vdatav.current_func_name = f.ident.toString();
            String tab = "  ";
            VVarRef.Local[] pms = f.params;   // function parameters
            String[] vs = f.vars;             // function local variables
            VCodeLabel[] lbl = f.labels;      // code labels
            VInstr[] bdy = f.body;            // instructions

            int func_start_line = f.sourcePos.line;
            int func_end_line = bdy[bdy.length - 1].sourcePos.line;

            vdatav.end_line = func_end_line;

            ArrayList<String> diflst = new ArrayList<String>();
            ArrayList<String> loclst = new ArrayList<String>();

            HashMap<Integer,String> lmap = new HashMap<Integer,String>();
            HashMap<String,String> v2reg = new HashMap<String,String>();

            // System.out.println(f.ident);
            // System.out.println("Params: ");
            vdatav.func_args = new Vector<String>();
            for (VVarRef.Local p : pms)
            {
                // System.out.println(tab + p.ident);
                diflst.add(p.ident);
                vdatav.func_args.add(p.ident.toString());
            }
            // System.out.println("Locals:");
            for (String v : vs)
            {
                // System.out.println(tab + v);
                loclst.add(v);
                v2reg.put(v, "");
            }
            // System.out.println("Labels:");
            for (VCodeLabel l : lbl)
            {
                // System.out.println(tab + "pos: " + l.sourcePos + "    " + l.ident );
                lmap.put(l.sourcePos.line, l.ident);
            }

            //jank
            vdatav.vartoreg = v2reg;

            // calculate the in/out/locals
            int in,out,local;
            int argno;
            in = 0; out = 0; local = 0;
            argno = 0;

            argno = diflst.size();
            // local = vdatav.all_mappings.local_list.size() + function_locals.get(k);
            local = vdatav.all_mappings.local_list.size() + 13;

            if(function_outs.get(k) > 4) {
                out = function_outs.get(k) - 4;
            }

            //inside visitor, keeps track of what register each variable encountered is mapped to
            vdatav.curr_vartoreg_mappings = new HashMap();

            if(argno >= 1) // get the parameters
            {
                for(int i = 0; i < argno; i++)
                {
                    // System.out.println("  " + "$t" + Integer.toString(i) + " = $a" + Integer.toString(i));
                    if(i < 4) {
                        String arg_reg = "$a" + i;
                        vdatav.curr_vartoreg_mappings.put(diflst.get(i).toString(), arg_reg);
                        // System.out.println(" " + var_loc + " = " + arg_reg);
                    }
                    else {
                        int in_index = i - 4;
                        String in_stack = "in[" + in_index + "]";
                        vdatav.curr_vartoreg_mappings.put(diflst.get(i), in_stack);
                        // System.out.println(" " + diflst.get(i).toString() + " = "+ in_stack);
                        in++;
                    }
                    vdatav.rnum++;  // inc reg cnt
                }
            }

            System.out.println("func " + f.ident + " [in " + Integer.toString(in)   // function interface
            + ", out " + Integer.toString(out) + ", local "
            + Integer.toString(local) + "]");

            // if(local >= 1) // save regs on enter
            // {
            //     int spill_index = vdatav.all_mappings.local_list.size();
            //     Vector<String> current_locals = save_reg.get(k);
            //     for(int arg_index = 0; arg_index < current_locals.size(); arg_index++)
            //     {
            //             String curr_arg = current_locals.get(arg_index).toString();
            //             String store_reg = vdatav.all_mappings.get_first_mapping(curr_arg, func_start_line, func_end_line, vdatav.curr_vartoreg_mappings);
            //             // if(curr_arg.equals(anObject))
            //             System.out.println(tab + "local[" + spill_index + "] = " + store_reg);
            //             spill_index++;
            //     }
            // }

            if(!f.ident.toString().equals("Main")) {
                int local_index = vdatav.all_mappings.local_list.size();

                System.out.println("  local[" + local_index + "] = $t0");
                local_index++;
                System.out.println("  local[" + local_index + "] = $t1");
                local_index++;
                System.out.println("  local[" + local_index + "] = $t2");
                local_index++;
                System.out.println("  local[" + local_index + "] = $t3");
                local_index++;
                System.out.println("  local[" + local_index + "] = $t4");
                local_index++;
                System.out.println("  local[" + local_index + "] = $t5");
                local_index++;
                System.out.println("  local[" + local_index + "] = $t6");
                local_index++;
                System.out.println("  local[" + local_index + "] = $t7");
                local_index++;
                System.out.println("  local[" + local_index + "] = $t8");
                local_index++;
                System.out.println("  local[" + local_index + "] = $a0");
                local_index++;
                System.out.println("  local[" + local_index + "] = $a1");
                local_index++;
                System.out.println("  local[" + local_index + "] = $a2");
                local_index++;
                System.out.println("  local[" + local_index + "] = $a3");
                local_index++;
            }



            //CODE FOR ALLOCATING REGISTERS ALL AT THE TOP
            String last_reg = "";
            for(int l = 0; l < argno; l++) {
                String arg_var = diflst.get(l).toString();
                String actual_var_reg = vdatav.all_mappings.get_first_mapping(arg_var, func_start_line, func_end_line, vdatav.curr_vartoreg_mappings);

                if(!actual_var_reg.equals("_") ) {
                    String arg_reg = vdatav.curr_vartoreg_mappings.get(arg_var).toString();

                    if(!arg_reg.equals(actual_var_reg) && !last_reg.equals(actual_var_reg)) {
                        last_reg = actual_var_reg;
                        System.out.println(tab + actual_var_reg + " = " + arg_reg);
                        vdatav.curr_vartoreg_mappings.replace(arg_var, actual_var_reg);
                    }
                }
            }


            //CODE FOR PRINTING VARIABLES ASSOCIATED TO THEIR ARG LOCATIONS
            // for(int l = 0; l < argno; l++) {
            //     String arg_var = diflst.get(l).toString();
            //     String actual_var_reg = vdatav.all_mappings.get_first_mapping(arg_var, func_start_line, func_end_line);
            //     System.out.println(tab + arg_var + " = " + vdatav.curr_vartoreg_mappings.get(arg_var));
            // }

            Vector<String> current_line_reg_mappings = null;

            for (VInstr vi : bdy)   // visit the instructions && print labelas
            {

                int t = vi.sourcePos.line-1;
                // System.out.println("");
                // for(String s : lmap.values())
                // {
                //     System.out.println(s);
                // }
                // System.out.println("");

                while(lmap.containsKey(t))  // if the sourcePos + 1 = sourcePos of a label then print the label
                {
                    System.out.println(lmap.get(t)+":");
                    t--;
                }

                String classy = vi.getClass().toString();
                int current_line = vi.sourcePos.line;

                if(vdatav.all_mappings.registers.containsKey(current_line)) {
                    current_line_reg_mappings = vdatav.all_mappings.registers.get(current_line);
                }

                if(classy.equals("class cs132.vapor.ast.VReturn"))
                {
                    VReturn vr = (VReturn)vi;   // put ret val into $v0
                    if(vr.value != null)
                    {
                        classy = vr.value.getClass().toString();
                        String retv = vr.value.toString();
                        if(!classy.equals("class cs132.vapor.ast.VLitInt")) {
                            for(int n = 0; n < current_line_reg_mappings.size(); n++) {
                                if(current_line_reg_mappings.get(n).equals(retv)) {
                                    retv = "$t" + n;
                                }
                            }

                            if(retv.equals(vr.value.toString())) {
                                retv = vdatav.all_mappings.get_first_mapping(vr.value.toString(), func_start_line, func_end_line, vdatav.curr_vartoreg_mappings);
                            }
                        }
                        // String retv = vdatav.vartoreg.get(vr.value.toString());
                        System.out.println( tab + "$v0 = " + retv );
                    }

                    if(!f.ident.toString().equals("Main")) {
                        int local_index = vdatav.all_mappings.local_list.size();

                        System.out.println("  $t0 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $t1 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $t2 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $t3 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $t4 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $t5 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $t6 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $t7 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $t8 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $a0 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $a1 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $a2 = local[" + local_index + "]");
                        local_index++;
                        System.out.println("  $a3 = local[" + local_index + "]");
                        local_index++;
                    }

                    // if(local >= 1) // save regs on enter
                    // {
                    //     int spill_index = vdatav.all_mappings.local_list.size();
                    //     Vector<String> current_locals = save_reg.get(k);
                    //     for(int arg_index = 0; arg_index < current_locals.size(); arg_index++)
                    //     {
                    //             String curr_arg = current_locals.get(arg_index).toString();
                    //             String store_reg = vdatav.all_mappings.get_first_mapping(curr_arg, func_start_line, func_end_line, vdatav.curr_vartoreg_mappings);
                    //             // if(curr_arg.equals(anObject))
                    //             System.out.println(tab + store_reg + " = local[" + spill_index + "]" );
                    //             spill_index++;
                    //     }
                    // }
                }


                go_visit(vdatav,vi);


                // t = vi.sourcePos.line+1;
                // System.out.println("");
                // for(String s : lmap.values())
                // {
                //     System.out.println(s);
                // }
                // System.out.println("");

                // while(lmap.containsKey(t))  // if the sourcePos + 1 = sourcePos of a label then print the label
                // {
                //     System.out.println(lmap.get(t)+":");
                //     t++;
                // }
            }

            System.out.println("");

            vdatav.rnum = 0; // reset reg count for each function
        }

        System.out.println("");
    }

    //----------------------------------------
}