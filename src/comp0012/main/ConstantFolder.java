package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.TargetLostException;



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

		// Implement your optimization here

		// Loop through all methods in the class
		for (Method method : cgen.getMethods()) {
			Code code = method.getCode();
			if (code == null) continue; // Skip methods with no code
	
			MethodGen mg = new MethodGen(method, cgen.getClassName(), cpgen);
			InstructionList il = mg.getInstructionList();
			if (il == null) continue;
	
			// Perform constant folding
			performConstantFolding(il, cpgen);
			// TODO: THE REST OF THE TASKS HERE
	
			// Update the method
			mg.setInstructionList(il);
			mg.setMaxStack();  // Recompute stack size
			mg.setMaxLocals(); // Recompute locals
			cgen.replaceMethod(method, mg.getMethod());
		}
        
		this.optimized = gen.getJavaClass();
	}

	// TASK 1 - Simple folding
	private void performConstantFolding(InstructionList il, ConstantPoolGen cpgen) {
        InstructionFinder finder = new InstructionFinder(il);
        foldIntegerOperations(finder, il, cpgen);
        foldLongOperations(finder, il, cpgen);
        foldFloatOperations(finder, il, cpgen);
        foldDoubleOperations(finder, il, cpgen);
    }

	// Folding methods for each type. Done for add, sub, mul, div, rem ops
    private void foldIntegerOperations(InstructionFinder finder, InstructionList il, ConstantPoolGen cpgen) {
        Function<Instruction, Integer> extractor = instr -> (Integer) ((LDC) instr).getValue(cpgen);
        
        foldNumericBinaryOp(finder, il, "LDC LDC IADD", 
            (a, b) -> a + b, LDC.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC LDC ISUB", 
            (a, b) -> a - b, LDC.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC LDC IMUL", 
            (a, b) -> a * b, LDC.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC LDC IDIV", 
            (a, b) -> a / b, LDC.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC LDC IREM", 
            (a, b) -> a % b, LDC.class, extractor, cpgen);
    }

    private void foldLongOperations(InstructionFinder finder, InstructionList il, ConstantPoolGen cpgen) {
        Function<Instruction, Long> extractor = instr -> (Long) ((LDC2_W) instr).getValue(cpgen);
        
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W LADD", 
            (a, b) -> a + b, LDC2_W.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W LSUB", 
            (a, b) -> a - b, LDC2_W.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W LMUL", 
            (a, b) -> a * b, LDC2_W.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W LDIV", 
            (a, b) -> a / b, LDC2_W.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W LREM", 
            (a, b) -> a % b, LDC2_W.class, extractor, cpgen);
    }

    private void foldFloatOperations(InstructionFinder finder, InstructionList il, ConstantPoolGen cpgen) {
        Function<Instruction, Float> extractor = instr -> (Float) ((LDC) instr).getValue(cpgen);
        
        foldNumericBinaryOp(finder, il, "LDC LDC FADD", 
            (a, b) -> a + b, LDC.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC LDC FSUB", 
            (a, b) -> a - b, LDC.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC LDC FMUL", 
            (a, b) -> a * b, LDC.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC LDC FDIV", 
            (a, b) -> a / b, LDC.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC LDC FREM", 
            (a, b) -> a % b, LDC.class, extractor, cpgen);
    }

    private void foldDoubleOperations(InstructionFinder finder, InstructionList il, ConstantPoolGen cpgen) {
        Function<Instruction, Double> extractor = instr -> (Double) ((LDC2_W) instr).getValue(cpgen);
        
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W DADD", 
            (a, b) -> a + b, LDC2_W.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W DSUB", 
            (a, b) -> a - b, LDC2_W.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W DMUL", 
            (a, b) -> a * b, LDC2_W.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W DDIV", 
            (a, b) -> a / b, LDC2_W.class, extractor, cpgen);
        foldNumericBinaryOp(finder, il, "LDC2_W LDC2_W DREM", 
            (a, b) -> a % b, LDC2_W.class, extractor, cpgen);
    }

	// MAIN LOGIC. Generic method to fold for every type
    private <T> void foldNumericBinaryOp(
        InstructionFinder finder, InstructionList il,
        String pattern, 
        BinaryOperator<T> op,
        Class<? extends Instruction> constClass,
        Function<Instruction, T> valueExtractor,
        ConstantPoolGen cpgen
    ) {
        // Find instances of the pattern
        for (Iterator<InstructionHandle[]> it = finder.search(pattern); it.hasNext();) {
            InstructionHandle[] match = it.next();
            // Extract the values from the instructions and apply the operation
            T val1 = valueExtractor.apply(match[0].getInstruction());
            T val2 = valueExtractor.apply(match[1].getInstruction());
            T result = op.apply(val1, val2);
            // Replace the old instruction sequence with the result
            try {
                replaceWithConstant(il, match[0], match[2], createConstant(result, constClass, cpgen));
            } catch (TargetLostException e) {
                System.err.println("Skipping folding due to unresolved branches");
            }
        }
    }

	// Make the new constant, of correct type
    private Instruction createConstant(Object value, Class<? extends Instruction> constClass, ConstantPoolGen cpgen) {
        if (constClass == LDC.class) {
            if (value instanceof Integer) {
                return new LDC(cpgen.addInteger((Integer) value));
            } else if (value instanceof Float) {
                return new LDC(cpgen.addFloat((Float) value));
            }
        } else if (constClass == LDC2_W.class) {
            if (value instanceof Long) {
                return new LDC2_W(cpgen.addLong((Long) value));
            } else if (value instanceof Double) {
                return new LDC2_W(cpgen.addDouble((Double) value));
            }
        }
        throw new IllegalArgumentException("Unsupported constant type");
    }

	// Replace the sequence of instructions with single new constant
	private void replaceWithConstant(InstructionList il, InstructionHandle start, InstructionHandle end, Instruction constant) throws TargetLostException {
		// Append the new constant first (it gets a new InstructionHandle)
		InstructionHandle newConstHandle = il.append(constant);
		
		// Redirect all branches targeting the old instructions to the new constant
		// (eg if (3 + 4 > x) { ... } need to ensure the branch (if stmt) target of 3 + 4 is updated)
		for (InstructionHandle ih = start; ih != end.getNext(); ih = ih.getNext()) {
			if (ih.hasTargeters()) {
				for (InstructionTargeter targeter : ih.getTargeters()) {
					if (targeter instanceof BranchInstruction) {
						BranchInstruction branch = (BranchInstruction) targeter;
						// Update the branch target to the new constant
						branch.setTarget(newConstHandle);
					}
				}
			}
		}
		
		// Now safely delete the old instructions
		il.delete(start, end);
	}
	// END OF TASK 1 - Simple folding

	// TODO: THE REST OF THE TASKS HERE
	
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