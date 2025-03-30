package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.function.BinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.BranchInstruction;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.DCONST;
import org.apache.bcel.generic.FCONST;
import org.apache.bcel.generic.ICONST;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionTargeter;
import org.apache.bcel.generic.LCONST;
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

        // Do folding for each type
        foldIntegerOperations(finder, il);
        foldLongOperations(finder, il);
        foldFloatOperations(finder, il);
        foldDoubleOperations(finder, il);
    }

	// Value extractors as constants, to avoid repeated lambda creation. Need these for generics
    private static final Function<Instruction, Integer> ICONST_EXTRACTOR = 
        instr -> ((ICONST) instr).getValue().intValue();
    private static final Function<Instruction, Long> LCONST_EXTRACTOR = 
        instr -> ((LCONST) instr).getValue().longValue();
    private static final Function<Instruction, Float> FCONST_EXTRACTOR = 
        instr -> ((FCONST) instr).getValue().floatValue();
    private static final Function<Instruction, Double> DCONST_EXTRACTOR = 
        instr -> ((DCONST) instr).getValue().doubleValue();

    private void foldIntegerOperations(InstructionFinder finder, InstructionList il) {
        foldNumericBinaryOp(finder, il, "ICONST ICONST IADD", 
            (Integer a, Integer b) -> a + b, ICONST.class, ICONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "ICONST ICONST ISUB", 
            (Integer a, Integer b) -> a - b, ICONST.class, ICONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "ICONST ICONST IMUL", 
            (Integer a, Integer b) -> a * b, ICONST.class, ICONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "ICONST ICONST IDIV", 
            (Integer a, Integer b) -> a / b, ICONST.class, ICONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "ICONST ICONST IREM", 
            (Integer a, Integer b) -> a % b, ICONST.class, ICONST_EXTRACTOR);
    }

    private void foldLongOperations(InstructionFinder finder, InstructionList il) {
        foldNumericBinaryOp(finder, il, "LCONST LCONST LADD", 
            (Long a, Long b) -> a + b, LCONST.class, LCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "LCONST LCONST LSUB", 
            (Long a, Long b) -> a - b, LCONST.class, LCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "LCONST LCONST LMUL", 
            (Long a, Long b) -> a * b, LCONST.class, LCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "LCONST LCONST LDIV", 
            (Long a, Long b) -> a / b, LCONST.class, LCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "LCONST LCONST LREM", 
            (Long a, Long b) -> a % b, LCONST.class, LCONST_EXTRACTOR);
    }

    private void foldFloatOperations(InstructionFinder finder, InstructionList il) {
        foldNumericBinaryOp(finder, il, "FCONST FCONST FADD", 
            (Float a, Float b) -> a + b, FCONST.class, FCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "FCONST FCONST FSUB", 
            (Float a, Float b) -> a - b, FCONST.class, FCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "FCONST FCONST FMUL", 
            (Float a, Float b) -> a * b, FCONST.class, FCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "FCONST FCONST FDIV", 
            (Float a, Float b) -> a / b, FCONST.class, FCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "FCONST FCONST FREM", 
            (Float a, Float b) -> a % b, FCONST.class, FCONST_EXTRACTOR);
    }

    private void foldDoubleOperations(InstructionFinder finder, InstructionList il) {
        foldNumericBinaryOp(finder, il, "DCONST DCONST DADD", 
            (Double a, Double b) -> a + b, DCONST.class, DCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "DCONST DCONST DSUB", 
            (Double a, Double b) -> a - b, DCONST.class, DCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "DCONST DCONST DMUL", 
            (Double a, Double b) -> a * b, DCONST.class, DCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "DCONST DCONST DDIV", 
            (Double a, Double b) -> a / b, DCONST.class, DCONST_EXTRACTOR);
        foldNumericBinaryOp(finder, il, "DCONST DCONST DREM", 
            (Double a, Double b) -> a % b, DCONST.class, DCONST_EXTRACTOR);
    }

	// Generic method to fold for every type
    private <T> void foldNumericBinaryOp(
        InstructionFinder finder, InstructionList il,
        String pattern, 
        BinaryOperator<T> op,
        Class<? extends Instruction> constClass,
        Function<Instruction, T> valueExtractor
    ) {
        for (Iterator<InstructionHandle[]> it = finder.search(pattern); it.hasNext();) {
            InstructionHandle[] match = it.next();
            T val1 = valueExtractor.apply(match[0].getInstruction());
            T val2 = valueExtractor.apply(match[1].getInstruction());
            T result = op.apply(val1, val2);
            try {
                replaceWithConstant(il, match[0], match[2], createConstant(result, constClass));
            } catch (TargetLostException e) {
                System.err.println("Skipping folding due to unresolved branches");
            }
        }
    }

	// Make the new constant, of correct type
    private Instruction createConstant(Object value, Class<? extends Instruction> constClass) {
        if (constClass == ICONST.class) {
            return new ICONST((Integer) value);
        } else if (constClass == LCONST.class) {
            return new LCONST((Long) value);
        } else if (constClass == FCONST.class) {
            return new FCONST((Float) value);
        } else if (constClass == DCONST.class) {
            return new DCONST((Double) value);
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