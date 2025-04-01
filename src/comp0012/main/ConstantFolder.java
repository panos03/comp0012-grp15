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

    //Repeatedly optimise until no more changes can be made
    boolean classModified;
    
    do {
        classModified = false;
        
        //Get the class' methods (fresh copy for each iteration!)
        Method[] methods = cgen.getMethods();
        
        for (int methodIndex = 0; methodIndex < methods.length; methodIndex++) {
            Method method = methods[methodIndex];
            
            //Get the Code attribute for the method (contains the bytecode)
            Code code = method.getCode();
            
            if (code != null) {
                //MethodGen object lets us modify the method
                MethodGen methodGen = new MethodGen(method, cgen.getClassName(), cpgen);
                
                //Instruction list for modification
                InstructionList instructionList = methodGen.getInstructionList();
                
                if (instructionList != null && !instructionList.isEmpty()) {
                    //InstructionFinder to find patterns of instructions
                    InstructionFinder finder = new InstructionFinder(instructionList);
                    
                    //Flag to track if this method was modified in this pass
                    boolean methodModified = false;
                    
                    //Look for arithmetic operations with constant operands for all numeric types
                    //Pattern for int operations
                    String intPattern = "LDC LDC (IADD|ISUB|IMUL|IDIV|IREM)";
                    //Pattern for long operations
                    String longPattern = "LDC2_W LDC2_W (LADD|LSUB|LMUL|LDIV|LREM)";
                    //Pattern for float operations
                    String floatPattern = "LDC LDC (FADD|FSUB|FMUL|FDIV|FREM)";
                    //Pattern for double operations
                    String doublePattern = "LDC2_W LDC2_W (DADD|DSUB|DMUL|DDIV|DREM)";
                    
                    //Process integer operations (bitwise OR, combine multiple boolean results in case any methods made a change)
                    methodModified |= processIntegerOperations(finder, intPattern, instructionList, cpgen);
                    
                    //Process long operations
                    methodModified |= processLongOperations(finder, longPattern, instructionList, cpgen);
                    
                    //Process float operations
                    methodModified |= processFloatOperations(finder, floatPattern, instructionList, cpgen);
                    
                    //Process double operations
                    methodModified |= processDoubleOperations(finder, doublePattern, instructionList, cpgen);
                    
                    if (methodModified) {
                        //Update the method with any changes
                        methodGen.setInstructionList(instructionList);
                        
                        //Recalculate stack size and local variables
                        methodGen.setMaxStack();
                        methodGen.setMaxLocals();
                        
                        //Replace old method with modified one
                        Method oldMethod = method;
                        Method newMethod = methodGen.getMethod();
                        cgen.replaceMethod(oldMethod, newMethod);
                        
                        //Made a change, do another pass
                        classModified = true;
                    }
                }
            }
        }
    } while (classModified); //Keep optimising until no more changes can be made
    
    //Update entire class
    this.gen = cgen;
    this.optimized = gen.getJavaClass();
}

//Process int operations for simple folding
private boolean processIntegerOperations(InstructionFinder finder, String pattern, 
                                         InstructionList instructionList, ConstantPoolGen cpgen) {
    boolean modified = false;
    
    //Find all instruction patterns that match
    for (Iterator<InstructionHandle[]> it = finder.search(pattern); it.hasNext();) {
        InstructionHandle[] match = it.next();
        
        if (match.length >= 3 && match[0] != null && match[1] != null && match[2] != null) {
            //Get the instructions and operands
            org.apache.bcel.generic.LDC ldc1 = (org.apache.bcel.generic.LDC) match[0].getInstruction();
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
                    
                    //Perform operation!
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
                            validOperation = false; //Avoid division by zero
                        }
                    } else if (operation instanceof org.apache.bcel.generic.IREM) {
                        if (i2 != 0) {
                            result = i1 % i2;
                        } else {
                            validOperation = false; //Avoid modulo by zero
                        }
                    } else {
                        validOperation = false;
                    }
                    
                    if (validOperation) {
                        //New instruction with the computed result
                        org.apache.bcel.generic.LDC newInstruction = new org.apache.bcel.generic.LDC(
                                cpgen.addInteger(result));
                        
                        //New instruction handle
                        InstructionHandle newHandle = instructionList.insert(match[0], newInstruction);
                        
                        //Delete the old instructions
                        try {
                            instructionList.delete(match[0], match[2]);
                            modified = true;
                            
                            //Exit early in case finder messes up, potentially further optimise in next iterations
                            return true;
                        } catch (org.apache.bcel.generic.TargetLostException e) {
                            //Redirect any lost instruction targets
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
    
    return modified;
}

//Process long operations for simple folding
private boolean processLongOperations(InstructionFinder finder, String pattern, 
                                      InstructionList instructionList, ConstantPoolGen cpgen) {
    boolean modified = false;
    
    //Find all instruction patterns that match
    for (Iterator<InstructionHandle[]> it = finder.search(pattern); it.hasNext();) {
        InstructionHandle[] match = it.next();
        
        if (match.length >= 3 && match[0] != null && match[1] != null && match[2] != null) {
            //Get the instructions and operands
            org.apache.bcel.generic.LDC2_W ldc1 = (org.apache.bcel.generic.LDC2_W) match[0].getInstruction();
            org.apache.bcel.generic.LDC2_W ldc2 = (org.apache.bcel.generic.LDC2_W) match[1].getInstruction();
            org.apache.bcel.generic.Instruction operation = match[2].getInstruction();
            
            if (ldc1 != null && ldc2 != null && operation != null) {
                Object val1 = ldc1.getValue(cpgen);
                Object val2 = ldc2.getValue(cpgen);
                
                //Check if longs
                if (val1 instanceof Long && val2 instanceof Long) {
                    long l1 = (Long) val1;
                    long l2 = (Long) val2;
                    long result = 0;
                    boolean validOperation = true;
                    
                    //Perform operation!
                    if (operation instanceof org.apache.bcel.generic.LADD) {
                        result = l1 + l2;
                    } else if (operation instanceof org.apache.bcel.generic.LSUB) {
                        result = l1 - l2;
                    } else if (operation instanceof org.apache.bcel.generic.LMUL) {
                        result = l1 * l2;
                    } else if (operation instanceof org.apache.bcel.generic.LDIV) {
                        if (l2 != 0) {
                            result = l1 / l2;
                        } else {
                            validOperation = false; //Avoid division by zero
                        }
                    } else if (operation instanceof org.apache.bcel.generic.LREM) {
                        if (l2 != 0) {
                            result = l1 % l2;
                        } else {
                            validOperation = false; //Avoid modulo by zero
                        }
                    } else {
                        validOperation = false;
                    }
                    
                    if (validOperation) {
                        //New instruction with the computed result
                        org.apache.bcel.generic.LDC2_W newInstruction = new org.apache.bcel.generic.LDC2_W(
                                cpgen.addLong(result));
                        
                        //New instruction handle
                        InstructionHandle newHandle = instructionList.insert(match[0], newInstruction);
                        
                        //Delete the old instructions
                        try {
                            instructionList.delete(match[0], match[2]);
                            modified = true;
                            
                            //Exit early in case finder messes up, potentially further optimise in next iterations
                            return true;
                        } catch (org.apache.bcel.generic.TargetLostException e) {
                            //Redirect any lost instruction targets
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
    
    return modified;
}

//Process float operations for simple folding
private boolean processFloatOperations(InstructionFinder finder, String pattern, 
                                       InstructionList instructionList, ConstantPoolGen cpgen) {
    boolean modified = false;
    
    //Find all instruction patterns that match
    for (Iterator<InstructionHandle[]> it = finder.search(pattern); it.hasNext();) {
        InstructionHandle[] match = it.next();
        
        if (match.length >= 3 && match[0] != null && match[1] != null && match[2] != null) {
            //Get the instructions and operands
            org.apache.bcel.generic.LDC ldc1 = (org.apache.bcel.generic.LDC) match[0].getInstruction();
            org.apache.bcel.generic.LDC ldc2 = (org.apache.bcel.generic.LDC) match[1].getInstruction();
            org.apache.bcel.generic.Instruction operation = match[2].getInstruction();
            
            if (ldc1 != null && ldc2 != null && operation != null) {
                Object val1 = ldc1.getValue(cpgen);
                Object val2 = ldc2.getValue(cpgen);
                
                //Check if floats
                if (val1 instanceof Float && val2 instanceof Float) {
                    float f1 = (Float) val1;
                    float f2 = (Float) val2;
                    float result = 0;
                    boolean validOperation = true;
                    
                    //Perform operation!
                    if (operation instanceof org.apache.bcel.generic.FADD) {
                        result = f1 + f2;
                    } else if (operation instanceof org.apache.bcel.generic.FSUB) {
                        result = f1 - f2;
                    } else if (operation instanceof org.apache.bcel.generic.FMUL) {
                        result = f1 * f2;
                    } else if (operation instanceof org.apache.bcel.generic.FDIV) {
                        if (f2 != 0) {
                            result = f1 / f2;
                        } else {
                            validOperation = false; //Avoid division by zero
                        }
                    } else if (operation instanceof org.apache.bcel.generic.FREM) {
                        if (f2 != 0) {
                            result = f1 % f2;
                        } else {
                            validOperation = false; //Avoid modulo by zero
                        }
                    } else {
                        validOperation = false;
                    }
                    
                    if (validOperation) {
                        //New instruction with the computed result
                        org.apache.bcel.generic.LDC newInstruction = new org.apache.bcel.generic.LDC(
                                cpgen.addFloat(result));
                        
                        //New instruction handle
                        InstructionHandle newHandle = instructionList.insert(match[0], newInstruction);
                        
                        //Delete the old instructions
                        try {
                            instructionList.delete(match[0], match[2]);
                            modified = true;
                            
                            //Exit early in case finder messes up, potentially further optimise in next iterations
                            return true;
                        } catch (org.apache.bcel.generic.TargetLostException e) {
                            //Redirect any lost instruction targets
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
    
    return modified;
}

//Process double operations for simple folding
private boolean processDoubleOperations(InstructionFinder finder, String pattern, 
                                        InstructionList instructionList, ConstantPoolGen cpgen) {
    boolean modified = false;
    
    //Find all instruction patterns that match
    for (Iterator<InstructionHandle[]> it = finder.search(pattern); it.hasNext();) {
        InstructionHandle[] match = it.next();
        
        if (match.length >= 3 && match[0] != null && match[1] != null && match[2] != null) {
            //Get the instructions and operands
            org.apache.bcel.generic.LDC2_W ldc1 = (org.apache.bcel.generic.LDC2_W) match[0].getInstruction();
            org.apache.bcel.generic.LDC2_W ldc2 = (org.apache.bcel.generic.LDC2_W) match[1].getInstruction();
            org.apache.bcel.generic.Instruction operation = match[2].getInstruction();
            
            if (ldc1 != null && ldc2 != null && operation != null) {
                Object val1 = ldc1.getValue(cpgen);
                Object val2 = ldc2.getValue(cpgen);
                
                //Check if doubles
                if (val1 instanceof Double && val2 instanceof Double) {
                    double d1 = (Double) val1;
                    double d2 = (Double) val2;
                    double result = 0;
                    boolean validOperation = true;
                    
                    //Perform operation!
                    if (operation instanceof org.apache.bcel.generic.DADD) {
                        result = d1 + d2;
                    } else if (operation instanceof org.apache.bcel.generic.DSUB) {
                        result = d1 - d2;
                    } else if (operation instanceof org.apache.bcel.generic.DMUL) {
                        result = d1 * d2;
                    } else if (operation instanceof org.apache.bcel.generic.DDIV) {
                        if (d2 != 0) {
                            result = d1 / d2;
                        } else {
                            validOperation = false; //Avoid division by zero
                        }
                    } else if (operation instanceof org.apache.bcel.generic.DREM) {
                        if (d2 != 0) {
                            result = d1 % d2;
                        } else {
                            validOperation = false; //Avoid modulo by zero
                        }
                    } else {
                        validOperation = false;
                    }
                    
                    if (validOperation) {
                        //New instruction with the computed result
                        org.apache.bcel.generic.LDC2_W newInstruction = new org.apache.bcel.generic.LDC2_W(
                                cpgen.addDouble(result));
                        
                        //New instruction handle
                        InstructionHandle newHandle = instructionList.insert(match[0], newInstruction);
                        
                        //Delete the old instructions
                        try {
                            instructionList.delete(match[0], match[2]);
                            modified = true;
                            
                            //Exit early in case finder messes up, potentially further optimise in next iterations
                            return true;
                        } catch (org.apache.bcel.generic.TargetLostException e) {
                            //Redirect any lost instruction targets
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
    
    return modified;
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