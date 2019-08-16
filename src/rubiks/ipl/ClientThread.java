/*
 * A class to create a thread to solve each sub job concurrently. 
 */
package rubiks.ipl;

import static rubiks.bonus.Rubiks.PRINT_SOLUTION;

/**
 * @author alexsharifi
 */
public class ClientThread extends Thread{
        public Cube cube;
        public Communication communication;
        public int bound;
        public CubeCache cache;
        
        ClientThread(Cube cube, Communication communication, int bound, CubeCache cache){
            this.communication=communication;
            this.cube=cube;
            this.bound=bound;
            this.cache=cache;
        }
    
    public int solutions(Cube cube, CubeCache cache) {
        if (cube.isSolved()) {
            return 1;
        }

        if (cube.getTwists() >= cube.getBound()) {
            return 0;
        }

        // generate all possible cubes from this one by twisting it in
        // every possible way. Gets new objects from the cache
        Cube[] children = cube.generateChildren(cache);

        int result = 0;

        for (Cube child : children) {
            // recursion step
            int childSolutions = solutions(child, cache);
            if (childSolutions > 0) {
                result += childSolutions;
                if (PRINT_SOLUTION) {
                    child.print(System.err);
                }
            }
            // put child object in cache
            cache.put(child);
    }

    return result;
    }


    @Override
    public void run() {
        // cache used for cube objects. Doing new Cube() for every move
        // overloads the garbage collector

        int result = 0;
        cube.setBound(bound);
        result = solutions(cube, cache);
        communication.subResult.getAndAdd(result);

    }

}
