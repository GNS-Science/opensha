package org.opensha.commons.logicTree;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.opensha.commons.logicTree.LogicTreeLevel.FileBackedLevel;
import org.opensha.commons.logicTree.LogicTreeNode.FileBackedNode;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.modules.helpers.JSON_BackedModule;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;

public class LogicTreeBranch<E extends LogicTreeNode> implements Iterable<E>, Cloneable, Serializable,
Comparable<LogicTreeBranch<E>>, JSON_BackedModule {
	
	private ImmutableList<LogicTreeLevel> levels;
	private List<E> values;
	
	@SuppressWarnings("unused") // used by Gson
	protected LogicTreeBranch() {
		
	}
	
	/**
	 * Creates a new logic tree branch, copying the values from the given branch. Updates to this branch will not affect
	 * the passed in branch, and vice versa.
	 * @param branch
	 */
	public LogicTreeBranch(LogicTreeBranch<E> branch) {
		this(branch.levels, branch.values);
	}
	
	/**
	 * Creates a new logic tree branch with the given branch levels
	 * 
	 * @param levels
	 */
	public LogicTreeBranch(List<LogicTreeLevel> levels) {
		this(levels, null);
	}
	
	/**
	 * Creates a new logic tree branch with the given branch levels and values. External updates to the given values list
	 * will not affect this branch (they are copied at construction)
	 * @param levels
	 * @param values
	 */
	public LogicTreeBranch(List<LogicTreeLevel> levels, List<E> values) {
		init(levels, values);
	}
	
	private void init(List<LogicTreeLevel> levels, List<E> values) {
		Preconditions.checkNotNull(levels);
		Preconditions.checkState(!levels.isEmpty(), "Must supply at least 1 branch level");
		for (int i=0; i<levels.size(); i++)
			Preconditions.checkNotNull(levels.get(i), "Branch level at index %s is null", i);
		this.levels = ImmutableList.copyOf(levels);
		if (values == null) {
			values = new ArrayList<>(levels.size());
			for (int i=0; i<levels.size(); i++)
				values.add(null);
			this.values = values;
		} else {
			Preconditions.checkState(levels.size() == values.size());
			for (int i=0; i<values.size(); i++) {
				LogicTreeNode value = values.get(i);
				if (value != null) {
					LogicTreeLevel level = levels.get(i);
					Class<?> type = level.getType();
					Preconditions.checkState(type.isAssignableFrom(value.getClass()),
							"Value '%s' is not the correct type for '%s'", value.getName(), level.getName());
					Preconditions.checkState(level.isMember(value), "Value '%s' is not a member of '%s'",
							value.getName(), level.getName());
				}
			}
			this.values = new ArrayList<>(values);
		}
	}
	
	/**
	 * Gets the branch value matching the given type, or null if none exist
	 * 
	 * @param <T>
	 * @param clazz
	 * @return
	 */
	@SuppressWarnings("unchecked") // it is checked through isAssignableFrom
	public <T extends LogicTreeNode> T getValue(Class<? extends T> clazz) {
		for (LogicTreeNode node : values)
			if (node != null && clazz.isAssignableFrom(node.getClass()))
				return (T)node;
		return null;
	}
	
	/**
	 * Shortcut to getValue(clazz) != null
	 * 
	 * @param clazz
	 * @return
	 */
	public boolean hasValue(Class<? extends LogicTreeNode> clazz) {
		return getValue(clazz) != null;
	}
	
	/**
	 * Get the value for the given class. This unchecked version can be useful to get around some nasty
	 * Java generics issues, but use getValue(...) instead whenever possible
	 * 
	 * @param clazz
	 * @return
	 */
	public E getValueUnchecked(Class<? extends LogicTreeNode> clazz) {
		for (E node : values)
			if (node != null && clazz.isAssignableFrom(node.getClass()))
				return node;
		return null;
	}
	
	/**
	 * 
	 * @return number of logic tree branch nodes (including nulls)
	 */
	public int size() {
		return levels.size();
	}
	
	/**
	 * 
	 * @param index
	 * @return Logic tree branch node value at the given index
	 */
	public E getValue(int index) {
		return values.get(index);
	}
	
	/**
	 * 
	 * @param index
	 * @return level at the given index
	 */
	public LogicTreeLevel getLevel(int index) {
		return levels.get(index);
	}
	
	/**
	 * Sets the value for the given class to null.
	 * @param clazz
	 * @return true if a value was cleared
	 * @throws NoSuchElementException if no levels in this tree match the given class
	 */
	public boolean clearValue(Class<? extends LogicTreeNode> clazz) {
		boolean classFound = false;
		boolean cleared = false;
		for (int i=0; i<size(); i++) {
			LogicTreeNode value = values.get(i);
			if (clazz.isAssignableFrom(levels.get(i).getType())) {
				values.set(i, null);
				classFound = true;
				cleared = cleared || value != null;
			}
		}
		if (!classFound)
			throw new NoSuchElementException("Class not in logic tree: "+clazz);
		return cleared;
	}
	
	/**
	 * Sets the value at the given index to null.
	 * @param index
	 */
	public void clearValue(int index) {
		values.set(index, null);
	}
	
	/**
	 * Sets the given value in the branch. Cannot be null (use clearValue(clazz)).
	 * @param value
	 */
	public void setValue(E value) {
		for (int i=0; i<levels.size(); i++) {
			LogicTreeLevel level = levels.get(i);
			if (level.getType().isAssignableFrom(value.getClass()) && level.isMember(value)) {
				values.set(i, value);
				return;
			}
		}
		throw new IllegalArgumentException("Value '"+value.getName()+"' with type '"+value.getClass()
			+"' is not a valid member of any level of this logic tree branch");
	}
	
	public void setValue(int index, E value) {
		LogicTreeLevel level = levels.get(index);
		Preconditions.checkState(level.getType().isAssignableFrom(value.getClass()) && level.isMember(value));
		values.set(index, value);
	}
	
	/**
	 * 
	 * @return true if all branch values are non-null
	 */
	public boolean isFullySpecified() {
		for (LogicTreeNode val : values)
			if (val == null)
				return false;
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((levels == null) ? 0 : levels.hashCode());
		result = prime * result + ((values == null) ? 0 : values.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LogicTreeBranch other = (LogicTreeBranch) obj;
		if (levels == null) {
			if (other.levels != null)
				return false;
		} else if (!levels.equals(other.levels))
			return false;
		if (values == null) {
			if (other.values != null)
				return false;
		} else if (!values.equals(other.values))
			return false;
		return true;
	}
	
	/**
	 * 
	 * @param o
	 * @return true if the given branch has the same branch levels as this branch
	 */
	public boolean areLevelsEqual(LogicTreeBranch o) {
//		for (int i=0; i<levels.size(); i++) {
//			LogicTreeLevel mine = levels.get(i);
//			LogicTreeLevel theirs = o.levels.size() > i ? o.levels.get(i) : null;
//			System.out.println("MY LEVEL: "+mine);
//			System.out.println("THEIR LEVEL: "+theirs);
//			System.out.println("\tEQUAL? "+mine.equals(theirs));
//			LogicTreeNode myVal = values.get(i);
//			LogicTreeNode theirVal = theirs == null ? null : o.values.get(i);
//			System.out.println("MY VALUE: "+myVal);
//			System.out.println("THEIR VALUE: "+theirVal);
//			System.out.println("\tEQUAL? "+(myVal == null ? null : myVal.equals(theirVal)));
//		}
		return levels.equals(o.levels);
	}
	
	/**
	 * @param branch
	 * @return the number of logic tree branches that are non null in this branch and differ from the given
	 * branch.
	 */
	public int getNumAwayFrom(LogicTreeBranch<?> o) {
		Preconditions.checkArgument(areLevelsEqual(o), "Supplied branch has different levels");
		int away = 0;
		
		for (int i=0; i<levels.size(); i++) {
			LogicTreeNode mine = values.get(i);
			LogicTreeNode  theirs = o.values.get(i);
			
			if (mine != null && !mine.equals(theirs))
				away++;
		}
		
		return away;
	}

	/**
	 * 
	 * @param branch
	 * @return true if every non null value of this branch matches the given branch
	 */
	public boolean matchesNonNulls(LogicTreeBranch branch) {
		return getNumAwayFrom(branch) == 0;
	}
	
	/**
	 * Builds a file name using the encodeChoiceString method on each branch value, separated by undercores.
	 * Can be parsed with fromFileName(String).
	 * @return
	 */
	public String buildFileName() {
		String str = null;
		for (int i=0; i<size(); i++) {
			LogicTreeNode value = values.get(i);
			if (value == null)
				throw new IllegalStateException("Must be fully specified to build file name! (missing="
						+levels.get(i).getName()+")");
			if (str == null)
				str = "";
			else
				str += "_";
			str += value.getFilePrefix();
		}
		return str;
	}
	
	@Override
	public String toString() {
		String str = null;
		for (LogicTreeNode val : values) {
			if (str == null)
				str = ClassUtils.getClassNameWithoutPackage(getClass())+"[";
			else
				str += ", ";
//			str += ClassUtils.getClassNameWithoutPackage(getEnumEnclosingClass(val.getClass()))+"="+val.getShortName();
			if (val == null)
				str += "(null)";
			else
				str += val.getFilePrefix();
		}
		return str+"]";
	}
	
	public LogicTreeBranch copy() {
		return new LogicTreeBranch(levels, values);
	}

	@Override
	public Object clone() {
		return copy();
	}
	
	/**
	 * 
	 * @return relative branch weight of all non-null values, should be normalized externally
	 */
	public double getBranchWeight() {
		double wt = 1;
		for (LogicTreeNode value : values)
			if (value != null)
				wt *= value.getNodeWeight(this);
		return wt;
	}

	@Override
	public int compareTo(LogicTreeBranch o) {
		int size = Integer.min(size(), o.size());
		for (int i=0; i<size; i++) {
			LogicTreeNode val = getValue(i);
			LogicTreeNode oval = o.getValue(i);
			int cmp;
			if (val == null || oval == null) {
				if (val == null)
					cmp = oval == null ? 0 : -1;
				else
					cmp = 1;
			} else {
				cmp = val.getShortName().compareTo(oval.getShortName());
			}
			if (cmp != 0)
				return cmp;
		}
		return 0;
	}

	@Override
	public Iterator<E> iterator() {
		return values.iterator();
	}

	@Override
	public String getFileName() {
		return "logic_tree_branch.json";
	}

	@Override
	public String getName() {
		return "Logic Tree Branch";
	}

	@Override
	public Gson buildGson() {
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		builder.registerTypeHierarchyAdapter(LogicTreeNode.class, new NodeTypeAdapter(this));
		
		return builder.create();
	}
	
	private static class NodeTypeAdapter extends TypeAdapter<LogicTreeNode> {
		
		private LogicTreeBranch branch;

		public NodeTypeAdapter(LogicTreeBranch branch) {
			this.branch = branch;
		}

		@Override
		public void write(JsonWriter out, LogicTreeNode value) throws IOException {
			out.beginObject();
			
			out.name("name").value(value.getName());
			out.name("shortName").value(value.getShortName());
			out.name("prefix").value(value.getFilePrefix());
			out.name("weight").value(value.getNodeWeight(branch));
			if (value instanceof Enum<?>) {
				// write the enum class
				Class<?> enumClass = value.getClass();
				if (!enumClass.isEnum())
					enumClass = enumClass.getEnclosingClass();
				Preconditions.checkNotNull(enumClass, "Enum class null?");
				Preconditions.checkState(enumClass.isEnum(), "Enum enclosing class not an enum?");
				out.name("enumClass").value(enumClass.getName());
				out.name("enumName").value(((Enum<?>)value).name());
			} else if (!(value instanceof FileBackedNode)) {
				out.name("class").value(value.getClass().getName());
			}
			
			out.endObject();
		}

		@SuppressWarnings("unchecked")
		@Override
		public LogicTreeNode read(JsonReader in) throws IOException {
			String name = null;
			String shortName = null;
			String prefix = null;
			double weight = 0d;
			Class<? extends LogicTreeNode> clazz = null;
			Class<? extends Enum<? extends LogicTreeNode>> enumClass = null;
			String enumName = null;
			
			in.beginObject();
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "name":
					name = in.nextString();
					break;
				case "shortName":
					shortName = in.nextString();
					break;
				case "prefix":
					prefix = in.nextString();
					break;
				case "weight":
					weight = in.nextDouble();
					break;
				case "class":
					String className = in.nextString();
					try {
						Class<?> rawClass = Class.forName(className);
						clazz = (Class<? extends LogicTreeNode>)rawClass;
					} catch (ClassNotFoundException e) {
						System.err.println("WARNING: couldn't locate logic tree branch node class '"+className+"', "
								+ "loading plain/hardcoded version instead");
					} catch (ClassCastException e) {
						System.err.println("WARNING: logic tree branch node class '"+className+"' is of the wrong type, "
								+ "loading plain/hardcoded version instead");
					}
					break;
				case "enumClass":
					String enumClassName = in.nextString();
					try {
						Class<?> rawClass = Class.forName(enumClassName);
						enumClass = (Class<? extends Enum<? extends LogicTreeNode>>)rawClass;
					} catch (ClassNotFoundException e) {
						System.err.println("WARNING: couldn't locate logic tree branch node enum class '"+enumClassName+"', "
								+ "loading plain/hardcoded version instead");
					} catch (ClassCastException e) {
						System.err.println("WARNING: logic tree branch node class '"+enumClassName+"' is of the wrong type, "
								+ "loading plain/hardcoded version instead");
					}
					break;
				case "enumName":
					enumName = in.nextString();
					break;

				default:
					break;
				}
			}
			
			in.endObject();
			
			if (enumClass != null && enumName != null) {
				// load it as an enum
				for (Enum<? extends LogicTreeNode> option : enumClass.getEnumConstants())
					if (option.name().equals(enumName))
						return (LogicTreeNode) option;
			}
			
			if (clazz != null) {
				// try to load it as a class via default constructor
				try {
					Constructor<? extends LogicTreeNode> constructor = clazz.getDeclaredConstructor();
					constructor.setAccessible(true);
					
					LogicTreeNode instance = constructor.newInstance();
					return instance;
				} catch (Exception e) {
					System.err.println("Couldn't instantiate default no-arg constructor of declared logic tree node class, "
								+ "loading plain/hardcoded version instead");
				}
			}
			
			Preconditions.checkState(shortName != null || name != null, "Must supply either name or short name");
			if (name == null)
				name = shortName;
			else if (shortName == null)
				shortName = name;
			if (prefix == null)
				prefix = shortName;
			return new FileBackedNode(name, shortName, weight, prefix);
		}
		
	}

	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		out.beginArray();
		
		for (int i=0; i<size(); i++) {
			out.beginObject();
			
			// branch level
			LogicTreeLevel level = levels.get(i);
			
			out.name("level").beginObject();
			
			out.name("name").value(level.getName());
			out.name("shortName").value(level.getShortName());
			if (level.getType().isEnum()) {
				// it's an enum
				out.name("enumClass").value(level.getType().getName());
			} else if (!(level instanceof FileBackedLevel)) {
				out.name("class").value(level.getClass().getName());
			}
			out.endObject();
			
			// node value
			LogicTreeNode node = values.get(i);
			
			if (node != null) {
				out.name("value");
				
				gson.toJson(node, LogicTreeNode.class, out);
			}
			
			out.endObject();
		}
		
		out.endArray();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void initFromJSON(JsonReader in, Gson gson) throws IOException {
		List<LogicTreeLevel> levels = new ArrayList<>();
		List<E> values = new ArrayList<>();
		
		in.beginArray();
		
		while (in.hasNext()) {
			in.beginObject();
			
			LogicTreeLevel level = null;
			LogicTreeNode value = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "level":
					String name = null;
					String shortName = null;
					String enumClassName = null;
					String className = null;
					in.beginObject();
					
					while (in.hasNext()) {
						switch (in.nextName()) {
						case "name":
							name = in.nextString();
							break;
						case "shortName":
							shortName = in.nextString();
							break;
						case "enumClass":
							enumClassName = in.nextString();
							break;
						case "className":
							className = in.nextString();
							break;

						default:
							break;
						}
					}
					
					if (enumClassName != null) {
						// load it as an enum
						try {
							Class<?> rawClass = Class.forName(enumClassName);
							level = LogicTreeLevel.forEnumUnchecked(rawClass, name, shortName);
						} catch (ClassNotFoundException e) {
							System.err.println("WARNING: couldn't locate logic tree branch node enum class '"+enumClassName+"', "
									+ "loading plain/hardcoded version instead");
						} catch (ClassCastException e) {
							System.err.println("WARNING: logic tree branch node class '"+enumClassName+"' is of the wrong type, "
									+ "loading plain/hardcoded version instead");
						}
					}
					if (level == null && className != null) {
						// try to load it as a class via default constructor
						try {
							Class<?> rawClass = Class.forName(className);
							Class<? extends LogicTreeLevel> clazz = (Class<? extends LogicTreeLevel>)rawClass;
							Constructor<? extends LogicTreeLevel> constructor = clazz.getDeclaredConstructor();
							constructor.setAccessible(true);
							
							level = constructor.newInstance();
						} catch (ClassNotFoundException e) {
							System.err.println("WARNING: couldn't locate logic tree branch node class '"+className+"', "
									+ "loading plain/hardcoded version instead");
						} catch (ClassCastException e) {
							System.err.println("WARNING: logic tree branch node class '"+className+"' is of the wrong type, "
									+ "loading plain/hardcoded version instead");
						} catch (Exception e) {
							System.err.println("Couldn't instantiate default no-arg constructor of declared logic tree node class, "
										+ "loading plain/hardcoded version instead");
						}
					}
					if (level == null) {
						// file-backed
						level = new FileBackedLevel(name, shortName, null);
					}
					
					in.endObject();
					break;
				case "value":
					value = gson.fromJson(in, LogicTreeNode.class);
					break;

				default:
					break;
				}
			}
			Preconditions.checkNotNull(level, "Branch level not supplied at index %s", levels.size());
			E typedValue = null;
			if (value != null) {
				if (value instanceof FileBackedNode) {
					if (level instanceof FileBackedLevel)
						((FileBackedLevel)level).setChoice((FileBackedNode)value);
					else
						// convert to a hardcoded version
						level = new FileBackedLevel(level.getName(), level.getShortName(), (FileBackedNode)value);
				}
				try {
					typedValue = (E)value;
				} catch (ClassCastException e) {
					throw new IllegalStateException("Could not cast value '"+value.getName()+" to type", e);
				}
			}
			levels.add(level);
			values.add(typedValue);
//			System.out.println("Loaded level: "+level);
//			System.out.println("Loaded value: "+value);
			
			in.endObject();
		}
		
		in.endArray();
		init(ImmutableList.copyOf(levels), values);
	}
	
	public static void main(String[] args) throws IOException {
		List<LogicTreeLevel> levels = new ArrayList<>();
		List<LogicTreeNode> values = new ArrayList<>();
		
		FileBackedNode node = new FileBackedNode("Node 1", "Node1", 1d, "n1");
		levels.add(new FileBackedLevel("Level 1", "Level1", node));
		values.add(node);
		
		node = new FileBackedNode("Node 2", "Node2", 1d, "n2");
		levels.add(new FileBackedLevel("Level 2", "Level2", node));
		values.add(node);
		
		node = null;
		levels.add(new FileBackedLevel("Level 3", "Level3", node));
		values.add(node);
		
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels, values);
		String json = branch.getJSON();
		System.out.println(json);
		
		LogicTreeBranch<LogicTreeNode> branch2 = new LogicTreeBranch<>();
		branch2.initFromJSON(json);
		String json2 = branch2.getJSON();
		System.out.println("Branch equal? "+branch2.equals(branch));
		System.out.println("Num away? "+branch.getNumAwayFrom(branch2));
		System.out.println("JSON equal? "+json.equals(json2));
		
		System.out.println("***** ENUM test *****");
		
		levels.clear();
		values.clear();
		
		levels.add(LogicTreeLevel.forEnum(FaultModels.class, "Fault Model", "FM"));
		values.add(FaultModels.FM3_1);
		levels.add(LogicTreeLevel.forEnum(DeformationModels.class, "Deformation Model", "DM"));
		values.add(DeformationModels.GEOLOGIC);
		levels.add(LogicTreeLevel.forEnum(InversionModels.class, "Inversion Model", "IM"));
		values.add(null);
		
		branch = new LogicTreeBranch<>(levels, values);
		
		json = branch.getJSON();
		System.out.println(json);
		
		branch2 = new LogicTreeBranch<>();
		branch2.initFromJSON(json);
		json2 = branch2.getJSON();
		System.out.println("Branch equal? "+branch2.equals(branch));
		System.out.println("Num away? "+branch.getNumAwayFrom(branch2));
		System.out.println("JSON equal? "+json.equals(json2));
	}
	
}
