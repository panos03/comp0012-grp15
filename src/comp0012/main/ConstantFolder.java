package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ArithmeticInstruction;
import org.apache.bcel.generic.BIPUSH;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.ConversionInstruction;
import org.apache.bcel.generic.DCMPG;
import org.apache.bcel.generic.DCMPL;
import org.apache.bcel.generic.DCONST;
import org.apache.bcel.generic.DNEG;
import org.apache.bcel.generic.FCMPG;
import org.apache.bcel.generic.FCMPL;
import org.apache.bcel.generic.FCONST;
import org.apache.bcel.generic.FNEG;
import org.apache.bcel.generic.GETSTATIC;
import org.apache.bcel.generic.GotoInstruction;
import org.apache.bcel.generic.ICONST;
import org.apache.bcel.generic.IINC;
import org.apache.bcel.generic.ILOAD;
import org.apache.bcel.generic.INEG;
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.ISTORE;
import org.apache.bcel.generic.IfInstruction;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.LCMP;
import org.apache.bcel.generic.LCONST;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.generic.LNEG;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ReturnInstruction;
import org.apache.bcel.generic.SIPUSH;
import org.apache.bcel.generic.StoreInstruction;
import org.apache.bcel.generic.TargetLostException;
import org.apache.bcel.generic.Type;



public class ConstantFolder
{
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	public ConstantFolder(String classFilePath)
	{
		try{
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
        ConstantPoolGen cpgen = cgen.getConstantPool();
        System.out.println(original.getClassName());
        for (Method m : cgen.getMethods()) {
            // don't optimie the constructor
            if (m.getName().equals("<init>")) continue;

            cgen.replaceMethod(m, optimiseMethod(m, cpgen));
        }
        
		this.optimized = cgen.getJavaClass();
	}

    public Method optimiseMethod(Method orig, ConstantPoolGen orig_cpgen) {
        InstructionList il = new InstructionList();

        ArrayList<Number> stack = new ArrayList<>();
        InstructionHandle curr = new MethodGen(orig, orig.getName(), orig_cpgen).getInstructionList().getStart();

        HashMap<Integer, Number> variables = new HashMap<>();

        while (curr != null) {
            Instruction ins = curr.getInstruction();
            boolean jumped = false;
            
            if (ins instanceof LDC) {
                Number value = (Number) ((LDC) ins).getValue(orig_cpgen);
                stack.add(value);
            } else if (ins instanceof LDC2_W) {
                Number value = (Number) ((LDC2_W) ins).getValue(orig_cpgen);
                stack.add(value);
            } else if (ins instanceof BIPUSH) {
                Number value = ((BIPUSH) ins).getValue();
                stack.add(value);
            } else if (ins instanceof SIPUSH) {
                Number value = ((SIPUSH) ins).getValue();
                stack.add(value);
            } else if (ins instanceof ICONST) {
                Number value = ((ICONST) ins).getValue();
                stack.add(value);
            } else if (ins instanceof LCONST) {
                Number value = ((LCONST) ins).getValue();
                stack.add(value);
            } else if (ins instanceof FCONST) {
                Number value = ((FCONST) ins).getValue();
                stack.add(value);
            } else if (ins instanceof DCONST) {
                Number value = ((DCONST) ins).getValue();
                stack.add(value);
            } else if (ins instanceof StoreInstruction) {
                int local_id = ((StoreInstruction) ins).getIndex();
                variables.put(local_id, stack.removeLast());
            } else if (ins instanceof LoadInstruction) {
                int local_id = ((LoadInstruction) ins).getIndex();
                stack.add(variables.get(local_id));
            } else if (ins instanceof IINC) {
                IINC iinc_ins = (IINC) ins;
                int local_id = iinc_ins.getIndex();
                int increment = iinc_ins.getIncrement();

                variables.put(local_id, variables.get(local_id).intValue() + increment);
            } else if (ins instanceof ArithmeticInstruction) {
                if (ins instanceof INEG || ins instanceof LNEG || ins instanceof FNEG || ins instanceof DNEG) {
                    Number value = stack.removeLast();
                    stack.add(handleNegation(ins.getName(), value));
                } else {
                    Number b = stack.removeLast();
                    Number a = stack.removeLast();
                    stack.add(handleArithmetic(ins.getName(), a, b));
                }
            } else if (ins instanceof ConversionInstruction) {
                // stack stores generic Number so no work is necessary to handle these instructions
            } else if (ins instanceof GotoInstruction) {
                curr = ((GotoInstruction) ins).getTarget();
                jumped = true;
            } else if (ins instanceof IfInstruction) {
                String ins_name = ins.getName();
                boolean follow = false;

                if (ins_name.length() == 4) {
                    // handle comparison with 0 instructions
                    Number a = stack.removeLast();
                    ins_name = "if_icmp" + ins_name.substring(ins_name.length() - 2);
                    follow = handleBoolean(ins_name, a.intValue(), 0);

                } else {
                    Number b = stack.removeLast();
                    Number a = stack.removeLast();
                    follow = handleBoolean(ins_name, a.intValue(), b.intValue());
                }

                if (follow) {
                    curr = ((IfInstruction) ins).getTarget();
                    jumped = true;
                }
            } else if (ins instanceof LCMP) {
                long b = stack.removeLast().longValue();
                long a = stack.removeLast().longValue();

                if (a == b) stack.add(0);
                else if (a > b) stack.add(1);
                else stack.add(-1);
            } else if (ins instanceof FCMPG || ins instanceof FCMPL) {
                float b = stack.removeLast().floatValue();
                float a = stack.removeLast().floatValue();

                if (Float.isNaN(a) || Float.isNaN(b)) {
                    if (ins instanceof FCMPG) stack.add(1);
                    else stack.add(-1);
                } else {
                    if (a == b) stack.add(0);
                    else if (a > b) stack.add(1);
                    else stack.add(-1);
                }
            } else if (ins instanceof DCMPG || ins instanceof DCMPL) {
                double b = stack.removeLast().doubleValue();
                double a = stack.removeLast().doubleValue();

                if (Double.isNaN(a) || Double.isNaN(b)) {
                    if (ins instanceof DCMPG) stack.add(1);
                    else stack.add(-1);
                } else {
                    if (a == b) stack.add(0);
                    else if (a > b) stack.add(1);
                    else stack.add(-1);
                }
            } else if (ins instanceof GETSTATIC) {
                il.append(ins);
            } else if (ins instanceof INVOKEVIRTUAL) {
                // assumes the function is println
                Type arg_type = ((INVOKEVIRTUAL) ins).getArgumentTypes(orig_cpgen)[0];

                if (arg_type == Type.INT || arg_type == Type.BOOLEAN) {
                    int const_index = orig_cpgen.addInteger(stack.removeLast().intValue());
                    il.append(new LDC(const_index));
                } else if (arg_type == Type.LONG) {
                    int const_index = orig_cpgen.addLong(stack.removeLast().longValue());
                    il.append(new LDC2_W(const_index));
                } else if (arg_type == Type.FLOAT) {
                    int const_index = orig_cpgen.addFloat(stack.removeLast().floatValue());
                    il.append(new LDC(const_index));
                } else if (arg_type == Type.DOUBLE) {
                    int const_index = orig_cpgen.addDouble(stack.removeLast().doubleValue());
                    il.append(new LDC2_W(const_index));
                } else {
                    System.err.println("Unrecognised println arg type " + arg_type.toString());
                }

                il.append(ins);
            } else if (ins instanceof ReturnInstruction) {
                if (stack.size() == 1) {
                    Type arg_type = ((ReturnInstruction) ins).getType();

                    if (arg_type == Type.INT) {
                        int const_index = orig_cpgen.addInteger(stack.removeLast().intValue());
                        il.append(new LDC(const_index));
                    } else if (arg_type == Type.LONG) {
                        int const_index = orig_cpgen.addLong(stack.removeLast().longValue());
                        il.append(new LDC2_W(const_index));
                    } else if (arg_type == Type.FLOAT) {
                        int const_index = orig_cpgen.addFloat(stack.removeLast().floatValue());
                        il.append(new LDC(const_index));
                    } else if (arg_type == Type.DOUBLE) {
                        int const_index = orig_cpgen.addDouble(stack.removeLast().doubleValue());
                        il.append(new LDC2_W(const_index));
                    } else {
                        System.err.println("Unrecognised return arg type " + arg_type.toString());
                    }
                } else if (stack.size() != 0) {
                    throw new RuntimeException("Return called when more than one thing on the stack");
                }

                il.append(ins);
                break;

            } else {
                System.err.println("Unrecognised instruction " + ins);
            }


            if (!jumped)
                curr = curr.getNext();
        }

        // we assume methods never have arguments, so pass null arg types/names
        MethodGen res = new MethodGen(orig.getAccessFlags(), orig.getReturnType(), null, null, orig.getName(), orig.getClass().getName(), il, orig_cpgen);
        res.setMaxStack();
        res.setMaxLocals();
        return res.getMethod();
    }

    public Number handleNegation(String ins, Number value) {
        switch (ins) {
            case "ineg":
                return -value.intValue();
            case "lneg":
                return -value.longValue();
            case "fneg":
                return -value.floatValue();
            case "dneg":
                return -value.doubleValue();
            default:
                throw new RuntimeException("Unrecognised negation instruction " + ins);
        }
    }

    public Number handleArithmetic(String ins, Number a, Number b) {
        switch (ins) {
            case "dadd":
                return a.doubleValue() + b.doubleValue();
            case "ddiv":
                return a.doubleValue() / b.doubleValue();
            case "dmul":
                return a.doubleValue() * b.doubleValue();
            case "drem":
                return a.doubleValue() % b.doubleValue();
            case "dsub":
                return a.doubleValue() - b.doubleValue();
            case "fadd":
                return a.floatValue() + b.floatValue();
            case "fdiv":
                return a.floatValue() / b.floatValue();
            case "fmul":
                return a.floatValue() * b.floatValue();
            case "frem":
                return a.floatValue() % b.floatValue();
            case "fsub":
                return a.floatValue() - b.floatValue();
            case "iadd":
                return a.intValue() + b.intValue();
            case "iand":
                return a.intValue() & b.intValue();
            case "idiv":
                return a.intValue() / b.intValue();
            case "imul":
                return a.intValue() * b.intValue();
            case "ior":
                return a.intValue() | b.intValue();
            case "irem":
                return a.intValue() % b.intValue();
            case "ishl":
                return a.intValue() << b.intValue();
            case "ishr":
                return a.intValue() >> b.intValue();
            case "isub":
                return a.intValue() - b.intValue();
            case "iushr":
                return a.intValue() >>> b.intValue();
            case "ixor":
                return a.intValue() ^ b.intValue();
            case "ladd":
                return a.longValue() + b.longValue();
            case "land":
                return a.longValue() & b.longValue();
            case "ldiv":
                return a.longValue() / b.longValue();
            case "lmul":
                return a.longValue() * b.longValue();
            case "lor":
                return a.longValue() | b.longValue();
            case "lrem":
                return a.longValue() % b.longValue();
            case "lshl":
                return a.longValue() << b.longValue();
            case "lshr":
                return a.longValue() >> b.longValue();
            case "lsub":
                return a.longValue() - b.longValue();
            case "lushr":
                return a.longValue() >>> b.longValue();
            case "lxor":
                return a.longValue() ^ b.longValue();
            default:
                throw new RuntimeException("Unrecognised arithmetic instruction " + ins);
        }
    }

    public boolean handleBoolean(String ins, int a, int b) {
        switch (ins) {
            case "if_icmpeq":
                return a == b;
            case "if_icmpge":
                return a >= b;
            case "if_icmpgt":
                return a > b;
            case "if_icmple":
                return a <= b;
            case "if_icmplt":
                return a < b;
            case "if_icmpne":
                return a != b;
            default:
                throw new RuntimeException("Unrecognised boolean instruction " + ins);
        }
    }

	
	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}
