/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2010,2011,2014,2015,2016 Poul Henriksen and Michael Kolling
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package greenfoot.record;

import greenfoot.Actor;
import greenfoot.ObjectTracker;
import greenfoot.World;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;

import bluej.Config;
import bluej.debugger.gentype.JavaType;
import bluej.stride.framedjava.elements.CallElement;
import bluej.stride.framedjava.elements.CodeElement;
import bluej.stride.framedjava.elements.NormalMethodElement;
import bluej.stride.framedjava.elements.VarElement;
import bluej.utility.Debug;

/**
 * Builder for code sequences representing a recording of what the user has
 * done interactively to the world.
 */
public class GreenfootRecorder
{
    /** A map of known objects to their name as it appears in the code */
    private final IdentityHashMap<Object, String> objectNames;
    private final ArrayList<Object> actorNameQueue;
    private final ArrayList<CodeElement> code;
    private World world;
    
    public static final String METHOD_ACCESS = "private";
    public static final String METHOD_RETURN = "void";
    public static final String METHOD_NAME = "prepare";
    
    /**
     * Construct a new GreenfootRecorder instance.
     */
    public GreenfootRecorder()
    {
        objectNames = new IdentityHashMap<>();
        actorNameQueue = new ArrayList<>();
        code = new ArrayList<>();
    }

    /**
     * Record the interactive construction of an actor object.
     * @param actor   The newly constructed actor
     * @param args     The arguments supplied to the actor's constructor, as Java expresssions
     * @param paramTypes  The parameter types of the called constructor
     */
    public synchronized void createActor(Object actor, String[] args, JavaType[] paramTypes)
    {
        Class<?> theClass = actor.getClass();
        String name = nameActor(actor);
        if (name != null) {
            code.add(new VarElement(null, theClass.getCanonicalName(), name,  "new " + theClass.getCanonicalName()
                    + "(" + withCommas(args, paramTypes, false) + ")"));
        }
    }
    
    /**
     * Called when the prepare method is replayed to indicate that the actor's name should be recorded.
     * Returns the name assigned to the actor (or null on failure).
     * 
     * <p>This is called from the simulation thread (with the world locked), or from the createActor method
     * above, which is called from the Swing EDT.
     */
    public synchronized String nameActor(Object actor)
    {
        try {
            String name = ObjectTracker.getRObjectName(actor);
            if (name != null) {
                objectNames.put(actor, name);
                return name;
            } else {
                return null;
            }
        }
        catch (RemoteException e) {
            Debug.reportError("Error naming actor", e);
            return null;
        }
    }

    private synchronized void nameActors(List<Object> actors)
    {
        try {
            List<String> names = ObjectTracker.getRObjectNames(actors);
            if (names != null && names.size() == actors.size()) {
                for (int i = 0; i < actors.size(); i++)
                    objectNames.put(actors.get(i), names.get(i));
            }
        }
        catch (RemoteException e) {
            Debug.reportError("Error naming actors", e);
        }
    }
    
    /**
     * Insert commas and other necessary syntax into an argument list
     * @param args      The arguments to a method or constructor call (as Java expressions)
     * @param paramTypes  The parameter types of the method/constructor
     * @return  The arguments as a comma-separated list
     */
    private static String withCommas(String[] args, JavaType[] paramTypes, boolean isVarArgs)
    {
        if (args == null) {
            return "";
        }
        
        StringBuffer commaArgs = new StringBuffer();
        
        for (int i = 0; i < args.length;i++) {
            String arg = args[i].trim();
            if (arg.startsWith("{") && arg.endsWith("}")) {
                String paramTypeName;
                if (isVarArgs && i >= paramTypes.length - 1) {
                    paramTypeName = paramTypes[paramTypes.length - 1].toString();
                    paramTypeName = paramTypeName.substring(0,paramTypeName.length()-2);
                }
                else {
                    paramTypeName = paramTypes[i].toString();
                }
                arg = "new " + paramTypeName + " " + arg;
            }
            commaArgs.append(arg);
            if (i != args.length - 1) {
                commaArgs.append(", ");
            }
        }
        return commaArgs.toString();
    }
    
    /**
     * An actor was interactively added to the world: record the interaction
     * @param actor   The added actor
     * @param x       The actor's x position
     * @param y       The actor's y position
     */
    public synchronized void addActorToWorld(Actor actor, int x, int y)
    {
        String actorObjectName = objectNames.get(actor);
        if (null == actorObjectName) {
            //An actor that we don't know about is being added to the world: ignore
            return;
        }
        code.add(callElement("addObject(" + actorObjectName + "," + String.valueOf(x) + "," + String.valueOf(y) + ")"));
    }

    /**
     * Record an interactive method call on object (actor or world). Called after the method
     * successfully returns.
     * 
     * @param obj        The object on which the method was invoked
     * @param actorName  The assigned object name
     * @param method     The method being called
     * @param args       The arguments to the method, as Java expressions
     * @param paramTypes  The parameter types of the method
     */
    public synchronized void callActorMethod(Object obj, String actorName, Method method,
            String[] args, JavaType[] paramTypes)
    {
        if (obj != null && null == objectNames.get(obj) && obj != world) {
            //Method is being called on an actor we don't know about: ignore
            return;
        }
        String name;
        if (world != null && world == obj) {
            // Called on the world, so don't use the world's object name before the call:
            name = method.getName();
        }
        else {
            name = actorName + "." + method.getName();
        }
        code.add(callElement(name + "(" + withCommas(args, paramTypes, method.isVarArgs()) + ")"));
    }

    /**
     * Record an interactive static method call. Called after the method
     * successfully returns.
     * 
     * @param className  The name of the class to which the called method belongs
     * @param methodName  The method name
     * @param args       The arguments to the method, as a
     * @param argTypes
     */
    public void callStaticMethod(String className, Method method, String[] args, JavaType[] argTypes)
    {
        // No difference in syntax, so no need to replicate the code:
        callActorMethod(null, className, method, args, argTypes);
    }
    
    /**
     * Notify the recorder that it should clear its recording.
     * 
     * @param simulationStarted  Whether the simulation is now running.
     */
    public synchronized void clearCode(boolean simulationStarted)
    {
        code.clear();
        if (simulationStarted) {
            objectNames.clear();
        }
    }

    /**
     * Notify the recorder that a new world is being initialised. This is currently called from the
     * simulation thread (with the current world locked).
     */
    public synchronized void reset()
    {
        objectNames.clear();
        actorNameQueue.clear();
        clearCode(false);
    }
    
    /**
     * Notify the recorder that a new world has become the current world.
     * Called from the simulation thread.
     */
    public synchronized void setWorld(World newWorld)
    {
        world = newWorld;
    }

    /**
     * Record a dragged actor interaction. This is currently called from the simulation
     * thread (i.e. with the world locked).
     */
    public synchronized void moveActor(Actor actor, int xCell, int yCell)
    {
        String actorObjectName = objectNames.get(actor);
        if (null == actorObjectName) {
            // This could happen with programmatically generated actors (e.g. in a World's method)
            // if the user drags them around afterwards.
            // We'll just have to ignore it
            return;
        }
        code.add(callElement(actorObjectName + ".setLocation(" + String.valueOf(xCell) + "," + String.valueOf(yCell) + ")"));
    }

    /**
     * Record a remove actor interaction.
     */
    public void removeActor(Actor obj)
    {
        String actorObjectName = objectNames.get(obj);
        if (null == actorObjectName) {
            // This could happen with programmatically generated actors (e.g. in a World's method)
            // if the user tries to remove them afterwards.
            // We'll just have to ignore it
            return;
        }
        code.add(callElement("removeObject(" + actorObjectName + ")"));
        objectNames.remove(obj);
    }

    /**
     * Retrieve the code elements representing the interactions recorded up to this point.
     */
    public synchronized List<CodeElement> getCode()
    {
        return new LinkedList<CodeElement>(code);
    }

    public NormalMethodElement getPrepareMethod()
    {
        StringBuffer documentation = new StringBuffer();
        documentation.append(Config.getString("record.method.comment1")).append("\n");
        documentation.append(Config.getString("record.method.comment2"));
        
        return new NormalMethodElement(METHOD_ACCESS, METHOD_RETURN, METHOD_NAME,
                null, code, documentation.toString());
    }

    public CallElement getPrepareMethodCall()
    {
        return callElement(METHOD_NAME + "()");
    }
    
    private CallElement callElement(String content)
    {
        return new CallElement(content, content);
    }

    public void initialised()
    {
        nameActors(actorNameQueue);
        actorNameQueue.clear();
    }

    public void queueNameActor(Actor object)
    {
        actorNameQueue.add(object);
    }
}