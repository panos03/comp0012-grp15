package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.InstructionList;
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
	
	public void optimize() {
        ClassGen cgen = new ClassGen(original);
        ConstantPoolGen cpgen = cgen.getConstantPool();
    
        //Get the class' methods
        Method[] methods = cgen.getMethods();
        
        for (Method method : methods) {
            //Get the Code attribute for the method (contains the bytecode)
            Code code = method.getCode();
            
            if (code != null) {
                //MethodGen object lets us modify the method
                MethodGen methodGen = new MethodGen(method, cgen.getClassName(), cpgen);
                
                //Instruction list for modification
                InstructionList instructionList = methodGen.getInstructionList();
                // System.out.println("instruction list: " + instructionList.toString());
                
                if (instructionList != null && !instructionList.isEmpty()) {
                    //InstructionFinder to find patterns of instructions
                    InstructionFinder finder = new InstructionFinder(instructionList);
                    
                    //Look for arithmetic operations with constant operands
                    //E.g., two constants pushed to stack and an operation between them
                    String pattern = "LDC LDC (IADD|ISUB|IMUL|IDIV)";
                    
                    boolean modified = false;
                    
                    //Find all instruction patterns that match
                    for (Iterator<InstructionHandle[]> it = finder.search(pattern); it.hasNext();) {
                        InstructionHandle[] match = it.next();
                        
                        //For testing
                        // if (match.length == 3) {
                        //     //Operands
                        //     org.apache.bcel.generic.LDC ldc1 = (org.apache.bcel.generic.LDC) match[0].getInstruction();
                        //     org.apache.bcel.generic.LDC ldc2 = (org.apache.bcel.generic.LDC) match[1].getInstruction();
                            
                        //     //Value types
                        //     Object val1 = ldc1.getValue(cpgen);
                        //     Object val2 = ldc2.getValue(cpgen);
                            
                        //     //For testing
                        //     System.out.println("Found constants: " + val1 + " and " + val2);
                        // }

                        if (match.length >= 3 && match[0] != null && match[1] != null && match[2] != null) {
                            //Get the instructions and operands
                            org.apache.bcel.generic.LDC ldc1 = (org.apache.bcel.generic.LDC) match[0].getInstruction();
                            // System.out.println("ldc1: " + ldc1);
                            org.apache.bcel.generic.LDC ldc2 = (org.apache.bcel.generic.LDC) match[1].getInstruction();
                            org.apache.bcel.generic.Instruction operation = match[2].getInstruction();
                            
                            if (ldc1 != null && ldc2 != null && operation != null) {
                                Object val1 = ldc1.getValue(cpgen);
                                Object val2 = ldc2.getValue(cpgen);
                                
                                //Check if integers
                                if (val1 instanceof Integer && val2 instanceof Integer) {
                                    int i1 = (Integer) val1;
                                    int i2 = (Integer) val2;
                                    int result = 0;
                                    boolean validOperation = true;
                                    
                                    //Perform operation! //E.g., iadd, isub, imul, idiv
                                    if (operation instanceof org.apache.bcel.generic.IADD) {
                                        result = i1 + i2;
                                    } else if (operation instanceof org.apache.bcel.generic.ISUB) {
                                        result = i1 - i2;
                                    } else if (operation instanceof org.apache.bcel.generic.IMUL) {
                                        result = i1 * i2;
                                    } else if (operation instanceof org.apache.bcel.generic.IDIV) {
                                        if (i2 != 0) {
                                            result = i1 / i2;
                                        } else {
                                            validOperation = false; //In case of division by zero
                                        }
                                    } else {
                                        validOperation = false;
                                    }
                                    
                                    if (validOperation) {
                                        //New instruction with the computed result (e.g., replacing multiple instructions with a single LDC)
                                        org.apache.bcel.generic.LDC newInstruction = new org.apache.bcel.generic.LDC(
                                                cpgen.addInteger(result));
                                        
                                        //New instruction handle
                                        InstructionHandle newHandle = instructionList.insert(match[0], newInstruction);
                                        
                                        //Delete the old instructions (e.g., ldc some_num, ldc some_num, iadd)
                                        try {
                                            instructionList.delete(match[0], match[2]);
                                            modified = true;
                                        } catch (org.apache.bcel.generic.TargetLostException e) {
                                            //Redirect any lost instruction targets to this new instruction!
                                            for (InstructionHandle target : e.getTargets()) {
                                                for (org.apache.bcel.generic.InstructionTargeter targeter : target.getTargeters()) {
                                                    targeter.updateTarget(target, newHandle);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (modified) {
                        //Update the method
                        methodGen.setInstructionList(instructionList);
                        // System.out.println("instruction list: " + instructionList.toString());
                        
                        //Recalculate stack size and local variables
                        methodGen.setMaxStack();
                        methodGen.setMaxLocals();
                        
                        //Replace old method
                        cgen.replaceMethod(method, methodGen.getMethod());
                    }
                }
            }
        }
        
        //Update entire class
        this.gen = cgen;
        this.optimized = gen.getJavaClass();
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