package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.BIPUSH;
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
import org.apache.bcel.generic.LDC;
import org.apache.bcel.generic.LDC2_W;
import org.apache.bcel.generic.LoadInstruction;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.SIPUSH;
import org.apache.bcel.generic.StoreInstruction;
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
                    
                    // TASK 2: CONSTANT VARIABLES
                    // Find constant variables and their values - get map from variable index to constant value
                    Map<Integer, Number> constantVariables = findConstantVariables(instructionList, cpgen);
                    
                    if (!constantVariables.isEmpty()) {
                        // Replace all uses of these constant variables with their actual values
                        boolean localModified = optimizeConstantVariables(instructionList, cpgen, constantVariables);
                        if (localModified) {
                            modified = true;
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

	// TASK 2 methods
    
    // Finds variables that were initialized once and never modified
    private Map<Integer, Number> findConstantVariables(InstructionList instructionList, ConstantPoolGen cpgen) {
        Map<Integer, Number> constantVars = new HashMap<>();
        Map<Integer, Boolean> modifiedVars = new HashMap<>();
        
        // First pass: find all variable assignments and track which ones are modified more than once
        InstructionHandle handle = instructionList.getStart();
        while (handle != null) {
            Instruction instruction = handle.getInstruction();
            
            // Check for store instructions (variable assignments)
            if (instruction instanceof StoreInstruction) {
                int index = ((StoreInstruction) instruction).getIndex();
                
                // If this variable has been seen before, mark it as modified
                if (modifiedVars.containsKey(index)) {
                    modifiedVars.put(index, true);
                } else {
                    modifiedVars.put(index, false);
                }
            }
            
            handle = handle.getNext();
        }
        
        // Second pass: for variables that are only assigned once, find their constant values
        handle = instructionList.getStart();
        while (handle != null) {
            Instruction instruction = handle.getInstruction();
            
            // Look for constant assignments: LDC followed by STORE
            if (instruction instanceof LDC && handle.getNext() != null && 
                handle.getNext().getInstruction() instanceof StoreInstruction) {
                
                LDC ldc = (LDC) instruction;
                StoreInstruction store = (StoreInstruction) handle.getNext().getInstruction();
                int index = store.getIndex();
                
                // Only consider variables that aren't modified after initialization
                if (modifiedVars.containsKey(index) && !modifiedVars.get(index)) {
                    Object value = ldc.getValue(cpgen);
                    if (value instanceof Number) {
                        // Add to hashmap
                        constantVars.put(index, (Number) value);
                    }
                }
            }
            // For other constant types (BIPUSH, SIPUSH, etc.)
            else if ((instruction instanceof BIPUSH || instruction instanceof SIPUSH || 
                     instruction instanceof ICONST || instruction instanceof FCONST || 
                     instruction instanceof LCONST || instruction instanceof DCONST) && 
                     handle.getNext() != null && 
                     handle.getNext().getInstruction() instanceof StoreInstruction) {
                     
                Number value = null;
                if (instruction instanceof BIPUSH) {
                    value = ((BIPUSH) instruction).getValue();
                } else if (instruction instanceof SIPUSH) {
                    value = ((SIPUSH) instruction).getValue();
                } else if (instruction instanceof ICONST) {
                    value = ((ICONST) instruction).getValue();
                } else if (instruction instanceof FCONST) {
                    value = ((FCONST) instruction).getValue();
                } else if (instruction instanceof LCONST) {
                    value = ((LCONST) instruction).getValue();
                } else if (instruction instanceof DCONST) {
                    value = ((DCONST) instruction).getValue();
                }
                
                if (value != null) {
                    StoreInstruction store = (StoreInstruction) handle.getNext().getInstruction();
                    int index = store.getIndex();
                    
                    // Again only want vars that aren't modified twice
                    if (modifiedVars.containsKey(index) && !modifiedVars.get(index)) {
                        // Add to hashmap
                        constantVars.put(index, value);
                    }
                }
            }
            // For LDC2_W (for long and double values)
            else if (instruction instanceof LDC2_W && handle.getNext() != null && 
                     handle.getNext().getInstruction() instanceof StoreInstruction) {
                LDC2_W ldc = (LDC2_W) instruction;
                StoreInstruction store = (StoreInstruction) handle.getNext().getInstruction();
                int index = store.getIndex();
                
                // Again only want vars that aren't modified twice
                if (modifiedVars.containsKey(index) && !modifiedVars.get(index)) {
                    Object value = ldc.getValue(cpgen);
                    if (value instanceof Number) {
                        // Add to hashmap
                        constantVars.put(index, (Number) value);
                    }
                }
            }
            
            handle = handle.getNext();
        }
        
        return constantVars;
    }
    
    // Replaces constant vars with their relevant values
    private boolean optimizeConstantVariables(InstructionList instructionList, ConstantPoolGen cpgen, Map<Integer, Number> constantVars) {
        boolean modified = false;
        InstructionHandle handle = instructionList.getStart();
        
        while (handle != null) {
            
            // Check for load instructions (variable uses)
            if (handle.getInstruction() instanceof LoadInstruction) {
                LoadInstruction load = (LoadInstruction) handle.getInstruction();
                int index = load.getIndex();
                
                // If this is a constant variable, replace with its constant
                if (constantVars.containsKey(index)) {
                    Number value = constantVars.get(index);
                    Instruction newInst = null;
                    
                    // Create appropriate constant instruction based on the value type
                    if (value instanceof Integer) {
                        newInst = new LDC(cpgen.addInteger(value.intValue()));
                    } else if (value instanceof Float) {
                        newInst = new LDC(cpgen.addFloat(value.floatValue()));
                    } else if (value instanceof Long) {
                        newInst = new LDC2_W(cpgen.addLong(value.longValue()));
                    } else if (value instanceof Double) {
                        newInst = new LDC2_W(cpgen.addDouble(value.doubleValue()));
                    }
                    
                    if (newInst != null) {
                        // Insert new constant push instruction and delete load
                        InstructionHandle newHandle = instructionList.insert(handle, newInst);
                        
                        try {
                            instructionList.delete(handle);
                            modified = true;
                            
                            // Update any references to the deleted instruction
                            if (handle.hasTargeters()) {
                                for (InstructionTargeter targeter : handle.getTargeters()) {
                                    targeter.updateTarget(handle, newHandle);
                                }
                            }
                        } catch (TargetLostException e) {
                            for (InstructionHandle target : e.getTargets()) {
                                for (InstructionTargeter targeter : target.getTargeters()) {
                                    targeter.updateTarget(target, newHandle);
                                }
                            }
                        }
                    }
                }
            }
            
            handle = handle.getNext();
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