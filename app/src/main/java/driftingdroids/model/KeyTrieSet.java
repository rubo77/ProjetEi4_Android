/*  DriftingDroids - yet another Ricochet Robots solver program.
    Copyright (C) 2011-2014 Michael Henke

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package driftingdroids.model;

import java.util.Arrays;



/**
 * This class is a customized <tt>TrieSet</tt> version. It works only with values that
 * are DriftingDroids keys, as generated by the KeyMakerInt and KeyMakerLong classes:
 * <p>
 * * keys are primitive <tt>int</tt> or <tt>long</tt> values<br>
 * * keys consist of elements, which are the positions of the robots on the board<br>
 * * elements of a key are unique (no two robots are on the same position)<br>
 * * elements of a key are sorted (non-goal robots can be substituted for each other)<br>
 * <p>
 * Nodes of KeyTrieSet are of variable size: the key elements are unique and sorted, thus
 * the next element must be larger than the current one and the number of possible next element
 * values is reduced from the maximum number (boardSize) to (boardSize - 1 - currentElement).
 */
public final class KeyTrieSet {
    
    private static final int NODE_ARRAY_SHIFT = 16;
    private static final int NODE_ARRAY_SIZE = 1 << NODE_ARRAY_SHIFT;
    private static final int NODE_ARRAY_MASK = NODE_ARRAY_SIZE - 1;
    private final int[] rootNode;
    private int[][] nodeArrays;
    private int numNodeArrays, nextNode, nextNodeArray;
    
    private static final int LEAF_ARRAY_SHIFT = 16;
    private static final int LEAF_ARRAY_SIZE = 1 << LEAF_ARRAY_SHIFT;
    private static final int LEAF_ARRAY_MASK = LEAF_ARRAY_SIZE - 1;
    private int[][] leafArrays;
    private int numLeafArrays, nextLeaf, nextLeafArray;
    
    private final int nodeNumber, nodeNumberLong31, nodeShift, nodeMask;
    private final int leafShift, leafSize, leafMask;
    
    private final int[] nodeSizeLookup;
    private final int[] elementLookup;
    
    
    
    /**
     * Constructs an empty KeyTrieSet that is tuned to the keys generated by solving a specific board.
     * 
     * @param boardNumRobots number of robots on the board
     * @param boardSize width*height of the board
     * @param boardSizeNumBits number of bits required to represent any position on the board (size - 1)
     */
    public KeyTrieSet(final Board board) {
        this.nodeNumber = board.getNumRobots() - 1;
        this.nodeNumberLong31 = (board.getNumRobots()*board.sizeNumBits - 31 + (board.sizeNumBits - 1)) / board.sizeNumBits;
        this.nodeShift = board.sizeNumBits;
        this.nodeMask = (1 << board.sizeNumBits) - 1;
        this.leafShift = board.sizeNumBits - 5;
        this.leafSize = 1 << this.leafShift;
        this.leafMask = this.leafSize - 1;
        
        this.nodeArrays = new int[32][];
        this.rootNode = new int[NODE_ARRAY_SIZE];
        this.nodeArrays[0] = this.rootNode;
        this.numNodeArrays = 1;
        this.nextNode = board.size;             //root node already exists
        this.nextNodeArray = NODE_ARRAY_SIZE;   //first array already exists
        
        this.leafArrays = new int[32][];
        this.numLeafArrays = 0;
        this.nextLeaf = this.leafSize;  //no leaves yet, but skip leaf "0" because this is the special value
        this.nextLeafArray = 0;         //no leaf arrays yet
        
        this.nodeSizeLookup = new int[board.size];
        for (int i = 0;  i < this.nodeSizeLookup.length;  ++i) {
            this.nodeSizeLookup[i] = board.size - 1 - i;
        }
        this.elementLookup = new int[board.size];
        for (int i = 0;  i < this.elementLookup.length;  ++i) {
            this.elementLookup[i] = i;
        }
        for (int i = 0;  i < board.size;  ++i) {
            if (true == board.isObstacle(i)) {
                for (int j = 0;  j < i;  ++j) {
                    this.nodeSizeLookup[j] -= 1;
                }
                for (int j = i;  j < this.elementLookup.length;  ++j) {
                    this.elementLookup[j] -= 1;
                }
            }
        }
    }
    
    
    
    /**
     * Adds the specified <tt>int</tt> key to this set if it is not already present.
     * 
     * @param key to be added to this set
     * @return <code>true</code> if this set did not already contain the specified key
     */
    public final boolean add(int key) {
        //root node
        int[] nodeArray = this.rootNode;
        int nidx = key & this.nodeMask;
        //go through nodes (with compression)
        for (int nodeIndex, i = 1;  i < this.nodeNumber;  ++i) {
            final int elementThis = key & this.nodeMask;
            nodeIndex = nodeArray[nidx];
            key >>>= this.nodeShift;
            if (0 == nodeIndex) {
                // -> node index is null = unused
                //write current key as a "compressed branch" (negative node index)
                //exit immediately because no further nodes and no leaf need to be stored
                nodeArray[nidx] = ~key;     //negative
                return true;    //added
            } else if (0 > nodeIndex) {
                // -> node index is negative = used by a single "compressed branch"
                //exit immediately if previous and current keys are equal (duplicate)
                final int prevKey = ~nodeIndex;
                if (prevKey == key) {
                    return false;   //not added
                }
                //create a new node
                final int nodeSize = this.nodeSizeLookup[elementThis];
                if (this.nextNode + nodeSize >= this.nextNodeArray) {
                    if (this.nodeArrays.length <= this.numNodeArrays) {
                        this.nodeArrays = Arrays.copyOf(this.nodeArrays, this.nodeArrays.length << 1);
                    }
                    this.nodeArrays[this.numNodeArrays++] = new int[NODE_ARRAY_SIZE];
                    this.nextNode = this.nextNodeArray;
                    this.nextNodeArray += NODE_ARRAY_SIZE;
                }
                nodeIndex = this.nextNode;
                this.nextNode += nodeSize;
                nodeArray[nidx] = nodeIndex;
                //push previous "compressed branch" one node further
                nodeArray = this.nodeArrays[nodeIndex >>> NODE_ARRAY_SHIFT];
                final int elementPrev = prevKey & this.nodeMask;
                nidx = (nodeIndex & NODE_ARRAY_MASK) - this.elementLookup[elementThis] - 1;
                nodeArray[nidx + this.elementLookup[elementPrev]] = ~(prevKey >>> this.nodeShift);
                final int elementNext = key & this.nodeMask;
                nidx += this.elementLookup[elementNext];
            } else {
                // -> node index is positive = go to next node
                nodeArray = this.nodeArrays[nodeIndex >>> NODE_ARRAY_SHIFT];
                final int elementNext = key & this.nodeMask;
                nidx = (nodeIndex & NODE_ARRAY_MASK) + this.elementLookup[elementNext] - this.elementLookup[elementThis] - 1;
            }
        }
        //get leaf (with compression)
        int leafIndex = nodeArray[nidx];
        key >>>= this.nodeShift;
        if (0 == leafIndex) {
            // -> leaf index is null = unused
            //write current key as a "compressed branch" (negative leaf index)
            //exit immediately because no leaf needs to be stored
            nodeArray[nidx] = ~key; //negative
            return true;    //added
        } else if (0 > leafIndex) {
            // -> leaf index is negative = used by a single "compressed branch"
            //exit immediately if previous and current keys are equal (duplicate)
            final int prevKey = ~leafIndex;
            if (prevKey == key) {
                return false;   //not added
            }
            //create a new leaf
            if (this.nextLeaf >= this.nextLeafArray) {
                if (this.leafArrays.length <= this.numLeafArrays) {
                    this.leafArrays = Arrays.copyOf(this.leafArrays, this.leafArrays.length << 1);
                }
                this.leafArrays[this.numLeafArrays++] = new int[LEAF_ARRAY_SIZE];
                this.nextLeafArray += LEAF_ARRAY_SIZE;
            }
            leafIndex = this.nextLeaf;
            this.nextLeaf += this.leafSize;
            nodeArray[nidx] = leafIndex;
            //push the previous "compressed branch" further to the leaf
            final int lidx = (leafIndex & LEAF_ARRAY_MASK) + (prevKey & this.leafMask);
            this.leafArrays[leafIndex >>> LEAF_ARRAY_SHIFT][lidx] = (1 << (prevKey >>> this.leafShift));
        }
        final int[] leafArray = this.leafArrays[leafIndex >>> LEAF_ARRAY_SHIFT];
        final int lidx = (leafIndex & LEAF_ARRAY_MASK) + (key & this.leafMask);
        //set bit in leaf
        final int oldBits = leafArray[lidx];
        final int newBits = oldBits | (1 << (key >>> this.leafShift));
        if (oldBits != newBits) {
            leafArray[lidx] = newBits;
            return true;    //added
        } else {
            return false;   //not added
        }
    }
    
    
    
    /**
     * Adds the specified <tt>long</tt> key to this set if it is not already present.
     * 
     * @param key to be added to this set
     * @return <code>true</code> if this set did not already contain the specified key
     */
    public final boolean add(long key) {
        //root node
        int[] nodeArray = this.rootNode;
        int nidx = (int)key & this.nodeMask;
        int i;  //used by both for() loops
        //go through nodes (without compression because key is greater than "int")
        for (i = 1;  i < this.nodeNumberLong31;  ++i) {
            final int elementThis = (int)key & this.nodeMask;
            int nodeIndex = nodeArray[nidx];
            key >>>= this.nodeShift;
            if (0 == nodeIndex) {
                //create a new node
                final int nodeSize = this.nodeSizeLookup[elementThis];
                if (this.nextNode + nodeSize >= this.nextNodeArray) {
                    if (this.nodeArrays.length <= this.numNodeArrays) {
                        this.nodeArrays = Arrays.copyOf(this.nodeArrays, this.nodeArrays.length << 1);
                    }
                    this.nodeArrays[this.numNodeArrays++] = new int[NODE_ARRAY_SIZE];
                    this.nextNode = this.nextNodeArray;
                    this.nextNodeArray += NODE_ARRAY_SIZE;
                }
                nodeIndex = this.nextNode;
                this.nextNode += nodeSize;
                nodeArray[nidx] = nodeIndex;
            }
            nodeArray = this.nodeArrays[nodeIndex >>> NODE_ARRAY_SHIFT];
            final int elementNext = (int)key & this.nodeMask;
            nidx = (nodeIndex & NODE_ARRAY_MASK) + this.elementLookup[elementNext] - this.elementLookup[elementThis] - 1;
        }
        //go through nodes (with compression because key is inside "int" range now)
        for ( ;  i < this.nodeNumber;  ++i) {
            final int elementThis = (int)key & this.nodeMask;
            int nodeIndex = nodeArray[nidx];
            key >>>= this.nodeShift;
            if (0 == nodeIndex) {
                // -> node index is null = unused
                //write current key as a "compressed branch" (negative node index)
                //exit immediately because no further nodes and no leaf need to be stored
                nodeArray[nidx] = ~((int)key);  //negative
                return true;    //added
            } else if (0 > nodeIndex) {
                // -> node index is negative = used by a single "compressed branch"
                //exit immediately if previous and current keys are equal (duplicate)
                final int prevKey = ~nodeIndex;
                if (prevKey == (int)key) {
                    return false;   //not added
                }
                //create a new node
                final int nodeSize = this.nodeSizeLookup[elementThis];
                if (this.nextNode + nodeSize >= this.nextNodeArray) {
                    if (this.nodeArrays.length <= this.numNodeArrays) {
                        this.nodeArrays = Arrays.copyOf(this.nodeArrays, this.nodeArrays.length << 1);
                    }
                    this.nodeArrays[this.numNodeArrays++] = new int[NODE_ARRAY_SIZE];
                    this.nextNode = this.nextNodeArray;
                    this.nextNodeArray += NODE_ARRAY_SIZE;
                }
                nodeIndex = this.nextNode;
                this.nextNode += nodeSize;
                nodeArray[nidx] = nodeIndex;
                //push previous "compressed branch" one node further
                nodeArray = this.nodeArrays[nodeIndex >>> NODE_ARRAY_SHIFT];
                final int elementPrev = prevKey & this.nodeMask;
                nidx = (nodeIndex & NODE_ARRAY_MASK) - this.elementLookup[elementThis] - 1;
                nodeArray[nidx + this.elementLookup[elementPrev]] = ~(prevKey >>> this.nodeShift);
                final int elementNext = (int)key & this.nodeMask;
                nidx += this.elementLookup[elementNext];
            } else {
                // -> node index is positive = go to next node
                nodeArray = this.nodeArrays[nodeIndex >>> NODE_ARRAY_SHIFT];
                final int elementNext = (int)key & this.nodeMask;
                nidx = (nodeIndex & NODE_ARRAY_MASK) + this.elementLookup[elementNext] - this.elementLookup[elementThis] - 1;
            }
        }
        //get leaf (with compression)
        int leafIndex = nodeArray[nidx];
        key >>>= this.nodeShift;
        if (0 == leafIndex) {
            // -> leaf index is null = unused
            //write current key as a "compressed branch" (negative leaf index)
            //exit immediately because no leaf needs to be stored
            nodeArray[nidx] = ~((int)key);  //negative
            return true;    //added
        } else if (0 > leafIndex) {
            // -> leaf index is negative = used by a single "compressed branch"
            //exit immediately if previous and current keys are equal (duplicate)
            final int prevKey = ~leafIndex;
            if (prevKey == (int)key) {
                return false;   //not added
            }
            //create a new leaf
            if (this.nextLeaf >= this.nextLeafArray) {
                if (this.leafArrays.length <= this.numLeafArrays) {
                    this.leafArrays = Arrays.copyOf(this.leafArrays, this.leafArrays.length << 1);
                }
                this.leafArrays[this.numLeafArrays++] = new int[LEAF_ARRAY_SIZE];
                this.nextLeafArray += LEAF_ARRAY_SIZE;
            }
            leafIndex = this.nextLeaf;
            this.nextLeaf += this.leafSize;
            nodeArray[nidx] = leafIndex;
            //push the previous "compressed branch" further to the leaf
            final int lidx = (leafIndex & LEAF_ARRAY_MASK) + (prevKey & this.leafMask);
            this.leafArrays[leafIndex >>> LEAF_ARRAY_SHIFT][lidx] = (1 << (prevKey >>> this.leafShift));
        }
        final int[] leafArray = this.leafArrays[leafIndex >>> LEAF_ARRAY_SHIFT];
        final int lidx = (leafIndex & LEAF_ARRAY_MASK) + ((int)key & this.leafMask);
        //set bit in leaf
        final int oldBits = leafArray[lidx];
        final int newBits = oldBits | (1 << ((int)key >>> this.leafShift));
        if (oldBits != newBits) {
            leafArray[lidx] = newBits;
            return true;    //added
        } else {
            return false;   //not added
        }
    }
    
    
    
    public final long getBytesAllocated() {
        long result = 0;
        for (int i = 0;  i < this.numNodeArrays;  ++i) {
            result += this.nodeArrays[i].length << 2;
        }
        for (int i = 0;  i < this.numLeafArrays;  ++i) {
            result += this.leafArrays[i].length << 2;
        }
        return result;
    }
}
