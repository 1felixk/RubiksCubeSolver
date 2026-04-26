package rubikscube;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
public class Solver {
    // private Stack<Integer> solutionStack = new Stack<>();
    private String solutionString = "";
    private CubieCube initialState;
    private int[] solutionPath = new int[50]; // 50 is safely larger than any realistic solution
    private int solutionDepthPhase1 = 0;
    private int solutionDepthTotal = 0;
    private static final String[] moveNames = {
            "U", "U2", "U'", "R", "R2", "R'", "F", "F2", "F'",
            "D", "D2", "D'", "L", "L2", "L'", "B", "B2", "B'"
    };

    public static void main(String[] args) throws IOException, IncorrectFormatException {
        if (args.length < 2) {
            System.out.println("File names are not specified");
            System.out.println(
                    "usage: java " + MethodHandles.lookup().lookupClass().getName() + " input_file output_file");
            return;
        }
        System.out.println("Solving...");
        long startTime = System.currentTimeMillis();
        Tables.init();
        String inputFile = args[0];
        String outputFile = args[1];
        //javac src/rubikscube/Solver.java
        //java -cp src rubikscube.Solver grdedtestcases/scramble15.txt output15.txt
        /*
        for f in grdedtestcases/scramble*.txt
        do
            out="output$(basename $f | sed 's/scramble//')"
        java -cp src rubikscube.Solver "$f" "$out"
        done
        */
        try {
            //Read Input
            CubieCube cc = parseInput(inputFile);
            CoordCube Cube = new CoordCube();
            //Initialize CoordCube
            Cube.twist = cc.Twist();
            Cube.flip = cc.Flip();
            Cube.slice = cc.returnSlice();
            // olve
            Solver solver = new Solver();
            String rawsolution = solver.solve(Cube, cc);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            //Post-process
            String processedSolution = convertToRawSequence(rawsolution);
            System.out.println("Solved!");
            System.out.println("Solution found in " + (duration / 1000.0) + "s");
            System.out.println("Solution for " + inputFile + ": ");
            System.out.println(processedSolution);
            System.out.println("");
            //Write Output
            FileWriter writer = new FileWriter(outputFile);
            writer.write(processedSolution);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String convertToRawSequence(String solution) {
        if (solution == null || solution.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        //Split by space to get individual moves (e.g., "U", "F2", "L'")
        String[] moves = solution.trim().split("\\s+");
        //Checks all moves values
        for (int i = 0; i < moves.length; i++) {
            String move = moves[i]; // Access the element at index i
            if (move.isEmpty()) continue;
            char face = move.charAt(0);
            if (move.length() == 1) {
                //Normal move dont change
                sb.append(face);
            } else if (move.endsWith("2")) {
                //Double move - convert
                sb.append(face).append(face);
            } else if (move.endsWith("'")) {
                //Prime move - convert
                sb.append(face).append(face).append(face);
            }
        }
        return sb.toString();
    }

    public String solve(CoordCube c, CubieCube initial) {
        this.initialState = initial;
        int depth = 0;
        while (true) {
            int result = Phase1(c, 0, depth);
            if (result == 0)
                return solutionString;
            depth++;
        }
    }

    private int Phase1(CoordCube node, int g, int maxDepth) {
        //Compute heuristic
        int h = phase1Heuristic(node);
        if (g + h > maxDepth)
            return g + h;
        if (h == 0) {
            solutionDepthPhase1 = g;
            return initPhase2(node);
        }
        int min = Integer.MAX_VALUE;
        for (int move = 0; move < 18; move++) {
            if (g > 0) {
                int prevMove = solutionPath[g - 1];
                int face = move / 3;
                int prevFace = prevMove / 3;
                if (face == prevFace)
                    continue;
                if (face == 3 && prevFace == 0)
                    continue;
                if (face == 1 && prevFace == 4)
                    continue;
                if (face == 2 && prevFace == 5)
                    continue;
            }
            CoordCube next = new CoordCube(node);
            next.movePhase1(move);
            solutionPath[g] = move;
            int res = Phase1(next, g + 1, maxDepth);
            if (res == 0)
                return 0;
            if (res < min)
                min = res;
        }
        return min;
    }


    private int initPhase2(CoordCube node) {
        //Reconstruct from initial cube using only Phase 1 moves
        CubieCube current = new CubieCube(initialState);
        for (int i = 0; i < solutionDepthPhase1; i++) {
            current.move(solutionPath[i]);
        }
        //Compute Phase 2 coords
        node.edge4 = (short) current.returnEdge4();
        node.corner = (short) current.returnCorner();
        node.slicePhase2 = (short) current.returnSlicePhase2();
        //Start Phase 2 search
        for (int d = 0; d < 18; d++) {
            int res = Phase2(node, 0, d);
            if (res == 0)
                return 0;
        }
        return -1;
    }


    private int Phase2(CoordCube node, int g, int maxDepth) {
        //Compute heuristic
        int h = phase2Heuristic(node);
        if (g + h > maxDepth)
            return g + h;
        if (h == 0) {
            solutionDepthTotal = solutionDepthPhase1 + g;
            buildSolutionString();
            return 0;
        }
        int[] allowedMoves = {0, 1, 2, 4, 7, 9, 10, 11, 13, 16};
        int min = Integer.MAX_VALUE;
        for (int move : allowedMoves) {
            int prevMove = -1;
            if (g > 0) {
                prevMove = solutionPath[solutionDepthPhase1 + g - 1];
            } else if (solutionDepthPhase1 > 0) {
                prevMove = solutionPath[solutionDepthPhase1 - 1];
            }
            if (prevMove != -1) {
                int face = move / 3;
                int prevFace = prevMove / 3;
                if (g > 0 && face == prevFace)
                    continue;
                if (face == 0 && prevFace == 3)
                    continue;
                if (face == 1 && prevFace == 4)
                    continue;
                if (face == 2 && prevFace == 5)
                    continue;
            }
            CoordCube next = new CoordCube(node);
            next.move(move);
            solutionPath[solutionDepthPhase1 + g] = move;
            int res = Phase2(next, g + 1, maxDepth);
            if (res == 0)
                return 0;
            if (res < min)
                min = res;
        }
        return min;
    }
        private void buildSolutionString () {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < solutionDepthTotal; i++) {
                sb.append(moveNames[solutionPath[i]]).append(" ");
            }
            solutionString = sb.toString().trim();
        }

//Cubiecube conversion------------------------------------------------------
    private static CubieCube parseInput(String inputFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(inputFile));
        char[][] facelets = new char[6][9]; // U, R, F, D, L, B
        String[] lines = new String[9];
        for (int i = 0; i < 9; i++) {
            lines[i] = br.readLine();
        }
        br.close();
        //Parse all U
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                facelets[0][r * 3 + c] = lines[r].charAt(3 + c);
            }
        }
        //Same for L F R B
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                facelets[4][r * 3 + c] = lines[3 + r].charAt(c); // L
                facelets[2][r * 3 + c] = lines[3 + r].charAt(3 + c); // F
                facelets[1][r * 3 + c] = lines[3 + r].charAt(6 + c); // R
                facelets[5][r * 3 + c] = lines[3 + r].charAt(9 + c); // B
            }
        }
        //Parse D
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                facelets[3][r * 3 + c] = lines[6 + r].charAt(3 + c);
            }
        }
        //Map colors to faces
        char[] centers = new char[6];
        centers[0] = facelets[0][4];
        centers[1] = facelets[1][4];
        centers[2] = facelets[2][4];
        centers[3] = facelets[3][4];
        centers[4] = facelets[4][4];
        centers[5] = facelets[5][4];
        CubieCube cc = new CubieCube();
        java.util.function.Function<Character, Integer> getFace = (c) -> {
            for (int i = 0; i < 6; i++)
                if (centers[i] == c)
                    return i;
            return -1;
        };
        int[][] cornerFacelets = {
                { 0, 8, 1, 0, 2, 2 },
                { 0, 6, 2, 0, 4, 2 },
                { 0, 0, 4, 0, 5, 2 },
                { 0, 2, 5, 0, 1, 2 },
                { 3, 2, 2, 8, 1, 6 },
                { 3, 0, 4, 8, 2, 6 },
                { 3, 6, 5, 8, 4, 6 },
                { 3, 8, 1, 8, 5, 6 }
        };
        for (int i = 0; i < 8; i++) {
            int f1 = getFace.apply(facelets[cornerFacelets[i][0]][cornerFacelets[i][1]]);
            int f2 = getFace.apply(facelets[cornerFacelets[i][2]][cornerFacelets[i][3]]);
            int f3 = getFace.apply(facelets[cornerFacelets[i][4]][cornerFacelets[i][5]]);
            int ori = 0;
            if (f1 == 0 || f1 == 3)
                ori = 0;
            else if (f2 == 0 || f2 == 3)
                ori = 1;
            else if (f3 == 0 || f3 == 3)
                ori = 2;
            cc.co[i] = (byte) ori;
            cc.cp[i] = identifyCorner(f1, f2, f3);
        }
        // Edges
        int[][] edgeFacelets = {
                { 0, 5, 1, 1 },
                { 0, 7, 2, 1 },
                { 0, 3, 4, 1 },
                { 0, 1, 5, 1 },
                { 3, 5, 1, 7 },
                { 3, 1, 2, 7 },
                { 3, 3, 4, 7 },
                { 3, 7, 5, 7 },
                { 2, 5, 1, 3 },
                { 2, 3, 4, 5 },
                { 5, 5, 4, 3 },
                { 5, 3, 1, 5 }
        };
        for (int i = 0; i < 12; i++) {
            int f1 = getFace.apply(facelets[edgeFacelets[i][0]][edgeFacelets[i][1]]);
            int f2 = getFace.apply(facelets[edgeFacelets[i][2]][edgeFacelets[i][3]]);

            int ori = 1;
            if (f1 == 0 || f1 == 3)
                ori = 0;
            else if ((f1 == 2 || f1 == 5) && (f2 == 1 || f2 == 4))
                ori = 0;
            cc.eo[i] = (byte) ori;
            cc.ep[i] = identifyEdge(f1, f2);
        }
        return cc;
    }
    private static byte identifyEdge(int f1, int f2) {
        int mask = (1 << f1) | (1 << f2);
        if (mask == ((1) | (1 << 1)))
            return 0;
        if (mask == ((1) | (1 << 2)))
            return 1;
        if (mask == ((1) | (1 << 4)))
            return 2;
        if (mask == ((1) | (1 << 5)))
            return 3;
        if (mask == ((1 << 3) | (1 << 1)))
            return 4;
        if (mask == ((1 << 3) | (1 << 2)))
            return 5;
        if (mask == ((1 << 3) | (1 << 4)))
            return 6;
        if (mask == ((1 << 3) | (1 << 5)))
            return 7;
        if (mask == ((1 << 2) | (1 << 1)))
            return 8;
        if (mask == ((1 << 2) | (1 << 4)))
            return 9;
        if (mask == ((1 << 5) | (1 << 4)))
            return 10;
        if (mask == ((1 << 5) | (1 << 1)))
            return 11;
        return 0;
    }

    private static byte identifyCorner(int f1, int f2, int f3) {
        int mask = (1 << f1) | (1 << f2) | (1 << f3);
        if (mask == ((1) | (1 << 1) | (1 << 2)))
            return 0;
        if (mask == ((1) | (1 << 2) | (1 << 4)))
            return 1;
        if (mask == ((1) | (1 << 4) | (1 << 5)))
            return 2;
        if (mask == ((1) | (1 << 5) | (1 << 1)))
            return 3;
        if (mask == ((1 << 3) | (1 << 2) | (1 << 1)))
            return 4;
        if (mask == ((1 << 3) | (1 << 4) | (1 << 2)))
            return 5;
        if (mask == ((1 << 3) | (1 << 5) | (1 << 4)))
            return 6;
        if (mask == ((1 << 3) | (1 << 1) | (1 << 5)))
            return 7;
        return 0;
    }
    // Heuristic helpers --------------------------------------
    private int phase1Heuristic(CoordCube node) {
        int twistH = Tables.twistPrunTable[node.twist];
        int flipH = Tables.flipPrunTable[node.flip];
        int sliceH = Tables.slicePrunTable[node.slice];
        return Math.max(twistH, Math.max(flipH, sliceH));
    }
    private int phase2Heuristic(CoordCube node) {
        int edge4H = Tables.edge4PrunTable[node.edge4 & 0xFFFF];
        int cornerH = Tables.cornerPrunTable[node.corner & 0xFFFF];
        int slice2H = Tables.slicePhase2PrunTable[node.slicePhase2];
        return Math.max(edge4H, Math.max(cornerH, slice2H));
    }
}
class Tables {
    public static short[][] flipMoveTable = new short[2048][18];
    public static short[][] twistMoveTable = new short[2187][18];
    public static short[][] sliceMoveTable = new short[495][18];
    public static char[][] cornerMoveTable = new char[40320][18];
    public static byte[][] slicePhase2MoveTable = new byte[24][18];
    public static char[][] edge4MoveTable = new char[40320][18];
    public static byte[] flipPrunTable = new byte[2048];
    public static byte[] twistPrunTable = new byte[2187];
    public static byte[] slicePrunTable = new byte[495];
    public static byte[] cornerPrunTable = new byte[40320];
    public static byte[] slicePhase2PrunTable = new byte[24];
    public static byte[] edge4PrunTable = new byte[40320];
    public static void init() {
        generateMoveTables();
        generatePruningTables();
    }
    private static void generateMoveTables() {
        CubieCube c = new CubieCube();
        //Twist
        for (int i = 0; i < 2187; i++) {
            c.setTwist((short) i);
            for (int j = 0; j < 18; j++) {
                CubieCube next = new CubieCube(c);
                next.move(j);
                twistMoveTable[i][j] = next.Twist();
            }
        }
        //Flip
        for (int i = 0; i < 2048; i++) {
            c.setFlip((short) i);
            for (int j = 0; j < 18; j++) {
                CubieCube next = new CubieCube(c);
                next.move(j);
                flipMoveTable[i][j] = next.Flip();
            }
        }
        //Slice
        for (int i = 0; i < 495; i++) {
            c.setSlice((short) i);
            for (int j = 0; j < 18; j++) {
                CubieCube next = new CubieCube(c);
                next.move(j);
                sliceMoveTable[i][j] = next.returnSlice();
            }
        }
        //Phase 2 Coordinates
        //Edge4
        for (int i = 0; i < 40320; i++) {
            c.setEdge4((short) i);
            for (int j = 0; j < 18; j++) {
                CubieCube next = new CubieCube(c);
                next.move(j);
                edge4MoveTable[i][j] = (char) next.returnEdge4();
            }
        }
        //Corner
        for (int i = 0; i < 40320; i++) {
            c.setCorner((short) i);
            for (int j = 0; j < 18; j++) {
                CubieCube next = new CubieCube(c);
                next.move(j);
                cornerMoveTable[i][j] = (char) next.returnCorner();
            }
        }
        //SlicePhase2
        for (int i = 0; i < 24; i++) {
            c.setSlicePhase2((short) i);
            for (int j = 0; j < 18; j++) {
                CubieCube next = new CubieCube(c);
                next.move(j);
                slicePhase2MoveTable[i][j] = (byte) next.returnSlicePhase2();
            }
        }
    }
    private static void generatePruningTables() {
        //Twist Pruning
        Arrays.fill(twistPrunTable, (byte) -1);
        twistPrunTable[0] = 0;
        solveBFS(twistPrunTable, twistMoveTable, 2187);
        //Flip Pruning
        Arrays.fill(flipPrunTable, (byte) -1);
        flipPrunTable[0] = 0;
        solveBFS(flipPrunTable, flipMoveTable, 2048);
        //Slice Pruning
        Arrays.fill(slicePrunTable, (byte) -1);
        slicePrunTable[0] = 0;
        solveBFS(slicePrunTable, sliceMoveTable, 495);
        //Edge4 Pruning
        Arrays.fill(edge4PrunTable, (byte) -1);
        edge4PrunTable[0] = 0;
        solveBFSPhase2(edge4PrunTable, edge4MoveTable, 40320);
        //Corner Pruning
        Arrays.fill(cornerPrunTable, (byte) -1);
        cornerPrunTable[0] = 0;
        solveBFSPhase2(cornerPrunTable, cornerMoveTable, 40320);
        //Slice Phase 2 Pruning
        Arrays.fill(slicePhase2PrunTable, (byte) -1);
        slicePhase2PrunTable[0] = 0;
        solveBFSPhase2(slicePhase2PrunTable, slicePhase2MoveTable, 24);
    }
    //BFS Utility Methods------------------------------------------------------
    private static void solveBFS(byte[] table, short[][] moveTable, int size) {
        Queue<Integer> queue = new ArrayDeque<>();
        //Start from the solved state
        queue.add(0);
        int count = 1;
        while (!queue.isEmpty() && count < size) {
            int state = queue.remove();
            int depth = table[state];
            for (int m = 0; m < 18; m++) {
                int next = moveTable[state][m];
                if (table[next] == -1) {
                    table[next] = (byte) (depth + 1);
                    queue.add(next);
                    count++;
                    if (count >= size) {
                        break;
                    }
                }
            }
        }
    }
    private static void solveBFSPhase2(byte[] table, char[][] moveTable, int size) {
        int[] allowedMoves = { 0, 1, 2, 4, 7, 9, 10, 11, 13, 16 };
        Queue<Integer> queue = new ArrayDeque<>();
        //solved state = 0
        queue.add(0);
        int count = 1;
        while (!queue.isEmpty() && count < size) {
            int state = queue.remove();
            int depth = table[state];
            for (int m : allowedMoves) {
                int next = moveTable[state][m];
                if (table[next] == -1) {
                    table[next] = (byte) (depth + 1);
                    queue.add(next);
                    count++;
                    if (count >= size) {
                        break;
                    }
                }
            }
        }
    }
    //Overload method (SlicePhase2)
    private static void solveBFSPhase2(byte[] table, byte[][] moveTable, int size) {
        int[] allowedMoves = { 0, 1, 2, 4, 7, 9, 10, 11, 13, 16 };
        Queue<Integer> queue = new ArrayDeque<>();
        //solved state = 0
        queue.add(0);
        int count = 1;
        while (!queue.isEmpty() && count < size) {
            int state = queue.remove();
            int depth = table[state];
            for (int m : allowedMoves) {
                int next = moveTable[state][m] & 0xFF; // Treat as unsigned
                if (table[next] == -1) {
                    table[next] = (byte) (depth + 1);
                    queue.add(next);
                    count++;
                    if (count >= size) {
                        break;
                    }
                }
            }
        }
    }
}
class CoordCube {
    // PHASE 1 COORDINATES
    public short twist;
    public short flip;
    public short slice;
    // PHASE 2 COORDINATES
    public short edge4;
    public short corner;
    public short slicePhase2;
    public CoordCube() {
        //Default constructor for solved state
        twist = 0;
        flip = 0;
        slice = 0;
        edge4 = 0;
        corner = 0;
        slicePhase2 = 0;
    }
    //Duplicate
    public CoordCube(CoordCube c) {
        this.twist = c.twist;
        this.flip = c.flip;
        this.slice = c.slice;
        this.edge4 = c.edge4;
        this.corner = c.corner;
        this.slicePhase2 = c.slicePhase2;
    }
    public void move(int move) {
        twist = Tables.twistMoveTable[twist][move];
        flip = Tables.flipMoveTable[flip][move];
        slice = Tables.sliceMoveTable[slice][move];
        // Phase 2 coords
        edge4 = (short) Tables.edge4MoveTable[edge4 & 0xFFFF][move];
        corner = (short) Tables.cornerMoveTable[corner & 0xFFFF][move];
        slicePhase2 = (byte) Tables.slicePhase2MoveTable[slicePhase2][move];
    }
    public void movePhase1(int move) {
        twist = Tables.twistMoveTable[twist][move];
        flip = Tables.flipMoveTable[flip][move];
        slice = Tables.sliceMoveTable[slice][move];
    }
}
class CubieCube {
    public byte[] cp = new byte[8];
    public byte[] co = new byte[8];
    public byte[] ep = new byte[12];
    public byte[] eo = new byte[12];
    //Temp buffers
    private static final byte[] tmpCp = new byte[8];
    private static final byte[] tmpCo = new byte[8];
    private static final byte[] tmpEp = new byte[12];
    private static final byte[] tmpEo = new byte[12];
    public CubieCube() {
        for (byte i = 0; i < 8; i++) {
            cp[i] = i;
            co[i] = 0;
        }
        for (byte i = 0; i < 12; i++) {
            ep[i] = i;
            eo[i] = 0;
        }
    }
    public CubieCube(CubieCube c) {
        System.arraycopy(c.cp, 0, this.cp, 0, 8);
        System.arraycopy(c.co, 0, this.co, 0, 8);
        System.arraycopy(c.ep, 0, this.ep, 0, 12);
        System.arraycopy(c.eo, 0, this.eo, 0, 12);
    }
    private static CubieCube[] basicMoves = new CubieCube[6];
    static {
        //U
        basicMoves[0] = new CubieCube();
        basicMoves[0].cp = new byte[]{3, 0, 1, 2, 4, 5, 6, 7};
        basicMoves[0].co = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        basicMoves[0].ep = new byte[]{3, 0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11};
        basicMoves[0].eo = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        //R
        basicMoves[1] = new CubieCube();
        basicMoves[1].cp = new byte[]{4, 1, 2, 0, 7, 5, 6, 3};
        basicMoves[1].co = new byte[]{2, 0, 0, 1, 1, 0, 0, 2};
        basicMoves[1].ep = new byte[]{8, 1, 2, 3, 11, 5, 6, 7, 4, 9, 10, 0};
        basicMoves[1].eo = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        //F
        basicMoves[2] = new CubieCube();
        basicMoves[2].cp = new byte[]{1, 5, 2, 3, 0, 4, 6, 7};
        basicMoves[2].co = new byte[]{1, 2, 0, 0, 2, 1, 0, 0};
        basicMoves[2].ep = new byte[]{0, 9, 2, 3, 4, 8, 6, 7, 1, 5, 10, 11};
        basicMoves[2].eo = new byte[]{0, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0};
        //D
        basicMoves[3] = new CubieCube();
        basicMoves[3].cp = new byte[]{0, 1, 2, 3, 5, 6, 7, 4};
        basicMoves[3].co = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        basicMoves[3].ep = new byte[]{0, 1, 2, 3, 5, 6, 7, 4, 8, 9, 10, 11};
        basicMoves[3].eo = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        //L
        basicMoves[4] = new CubieCube();
        basicMoves[4].cp = new byte[]{0, 2, 6, 3, 4, 1, 5, 7};
        basicMoves[4].co = new byte[]{0, 1, 2, 0, 0, 2, 1, 0};
        basicMoves[4].ep = new byte[]{0, 1, 10, 3, 4, 5, 9, 7, 8, 2, 6, 11};
        basicMoves[4].eo = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        //B
        basicMoves[5] = new CubieCube();
        basicMoves[5].cp = new byte[]{0, 1, 3, 7, 4, 5, 2, 6};
        basicMoves[5].co = new byte[]{0, 0, 1, 2, 0, 0, 2, 1};
        basicMoves[5].ep = new byte[]{0, 1, 2, 11, 4, 5, 6, 10, 8, 9, 3, 7};
        basicMoves[5].eo = new byte[]{0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 1};
    }
    public void multiply(CubieCube b) {
        for (int i = 0; i < 8; i++) {
            int src = b.cp[i];
            tmpCp[i] = this.cp[src];
            tmpCo[i] = (byte) ((this.co[src] + b.co[i]) % 3);
        }
        for (int i = 0; i < 12; i++) {
            int src = b.ep[i];
            tmpEp[i] = this.ep[src];
            tmpEo[i] = (byte) ((this.eo[src] + b.eo[i]) % 2);
        }
        for (int i = 0; i < 8; i++) {
            this.cp[i] = tmpCp[i];
            this.co[i] = tmpCo[i];
        }
        for (int i = 0; i < 12; i++) {
            this.ep[i] = tmpEp[i];
            this.eo[i] = tmpEo[i];
        }
    }
    public void move(int move) {
        int base = move / 3;
        int power = move % 3;
        CubieCube m = basicMoves[base];
        for (int p = 0; p <= power; p++) {
            this.multiply(m);
        }
    }

    //Twist
    public void setTwist(short twist) {
        int twistParity = 0;
        for (int i = 6; i >= 0; i--) {
            co[i] = (byte) (twist % 3);
            twistParity += co[i];
            twist /= 3;
        }
        co[7] = (byte) ((3 - twistParity % 3) % 3);
    }
    //Flip
    public void setFlip(short flip) {
        int flipParity = 0;
        for (int i = 10; i >= 0; i--) {
            eo[i] = (byte) (flip % 2);
            flipParity += eo[i];
            flip /= 2;
        }
        eo[11] = (byte) ((2 - flipParity % 2) % 2);
    }
    //Slice (Combination of 4 slice edges in 12 positions)
    public void setSlice(short slice) {
        int index = slice;
        Arrays.fill(ep, (byte) 0);
        int k = 4;
        for (int i = 0; i < 4; i++) {
            int v = k - 1;
            while (true) {
                if (Cnk(v + 1, k) > index)
                    break;
                v++;
            }
            ep[11 - v] = 8;
            index -= Cnk(v, k);
            k--;
        }
    }
    //Edge4
    public void setEdge4(short edge4) {
        int[] a = new int[8];
        java.util.List<Integer> available = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++)
            available.add(i);
        int idx = edge4 & 0xFFFF;
        int fact = 5040; // 7!
        for (int i = 0; i < 7; i++) {
            int q = idx / fact;
            idx %= fact;
            a[i] = available.remove(q);
            fact /= (7 - i);
        }
        a[7] = available.remove(0);
        for (int i = 0; i < 8; i++) {
            ep[i] = (byte) a[i];
        }
        for (int i = 8; i < 12; i++)
            ep[i] = (byte) i;
    }
    //Corner
    public void setCorner(short corner) {
        int[] a = new int[8];
        java.util.List<Integer> available = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++)
            available.add(i);
        int idx = corner & 0xFFFF;
        int fact = 5040;
        for (int i = 0; i < 7; i++) {
            int q = idx / fact;
            idx %= fact;
            a[i] = available.remove(q);
            fact /= (7 - i);
        }
        a[7] = available.remove(0);
        for (int i = 0; i < 8; i++) {
            cp[i] = (byte) a[i];
        }
    }
    //SlicePhase2
    public void setSlicePhase2(short slicePhase2) {
        int[] a = new int[4];
        java.util.List<Integer> available = new java.util.ArrayList<>();
        for (int i = 8; i < 12; i++)
            available.add(i);
        int idx = slicePhase2;
        int fact = 6; // 3!
        for (int i = 0; i < 3; i++) {
            int q = idx / fact;
            idx %= fact;
            a[i] = available.remove(q);
            fact /= (3 - i);
        }
        a[3] = available.remove(0);
        for (int i = 0; i < 4; i++) {
            ep[8 + i] = (byte) a[i];
        }
        for (int i = 0; i < 8; i++)
            ep[i] = (byte) i;
    }
    //Flip
    public short Flip() {
        short ret = 0;
        for (int i = 0; i < 11; i++) {
            ret = (short) (2 * ret + eo[i]);
        }
        return ret;
    }

    //Twist
    public short Twist() {
        short ret = 0;
        for (int i = 0; i < 7; i++) {
            ret = (short) (3 * ret + co[i]);
        }
        return ret;
    }

    //Slice
    public short returnSlice() {
        int a = 0;
        int x = 0;
        for (int j = 11; j >= 0; j--) {
            if (ep[j] >= 8) {
                a += Cnk(11 - j, x + 1);
                x++;
            }
        }
        return (short) a;
    }
    //Helper for Binomial Coefficient
    private int Cnk(int n, int k) {
        if (n < k)
            return 0;
        if (k == 0)
            return 1;
        if (k == 1)
            return n;
        if (k > n / 2)
            k = n - k;
        int res = 1;
        for (int i = 1; i <= k; i++) {
            res = res * (n - i + 1) / i;
        }
        return res;
    }
    //Corner
    public short returnCorner() {
        int[] a = new int[8];
        for (int i = 0; i < 8; i++)
            a[i] = cp[i];
        int idx = 0;
        for (int i = 0; i < 7; i++) {
            int count = 0;
            for (int j = i + 1; j < 8; j++) {
                if (a[j] < a[i])
                    count++;
            }
            idx += count * factorial(7 - i);
        }
        return (short) idx;
    }
    //SlicePhase2
    public short returnSlicePhase2() {
        int[] a = new int[4];
        for (int i = 0; i < 4; i++)
            a[i] = ep[8 + i];
        int idx = 0;
        for (int i = 0; i < 3; i++) {
            int count = 0;
            for (int j = i + 1; j < 4; j++) {
                if (a[j] < a[i])
                    count++;
            }
            idx += count * factorial(3 - i);
        }
        return (short) idx;
    }
    //Edge4
    public short returnEdge4() {
        int[] a = new int[8];
        for (int i = 0; i < 8; i++)
            a[i] = ep[i];

        int idx = 0;
        for (int i = 0; i < 7; i++) {
            int count = 0;
            for (int j = i + 1; j < 8; j++) {
                if (a[j] < a[i])
                    count++;
            }
            idx += count * factorial(7 - i);
        }
        return (short) idx;
    }

    private int factorial(int n) {
        if (n <= 1)
            return 1;
        int res = 1;
        for (int i = 2; i <= n; i++)
            res *= i;
        return res;
    }
}