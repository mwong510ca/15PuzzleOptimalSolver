package mwong.myprojects.fifteenpuzzle.solver.ai;

import mwong.myprojects.fifteenpuzzle.solver.FileProperties;
import mwong.myprojects.fifteenpuzzle.solver.HeuristicOptions;
import mwong.myprojects.fifteenpuzzle.solver.SmartSolver;
import mwong.myprojects.fifteenpuzzle.solver.SolverConstants;
import mwong.myprojects.fifteenpuzzle.solver.advanced.SmartSolverPdb;
import mwong.myprojects.fifteenpuzzle.solver.components.ApplicationMode;
import mwong.myprojects.fifteenpuzzle.solver.components.Board;
import mwong.myprojects.fifteenpuzzle.solver.components.Direction;
import mwong.myprojects.fifteenpuzzle.solver.components.PatternOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * ReferenceAccumulator implements Reference interface of the reference collections.
 * It has full features of load the storage, add or remove a board, change setting,
 * reset the collection, etc.
 *
 * <p>Dependencies : Board.java, Direction.java, FileProperties.java, HeuristicOptions.java,
 *                   PatternOptions.java, Reference.java, ReferenceBoard.java,
 *                   ReferenceConstants.java, ReferenceMoves.java, ReferenceProperties.java,
 *                   SmartSolver.java, SmartSolverpdb.java
 *
 * @author Meisze Wong
 *         www.linkedin.com/pub/macy-wong/46/550/37b/
 */
public class ReferenceAccumulator implements Reference {
    private final String directory;
    private final String filepath;
    private final String coreSolverClassName;
    private final HeuristicOptions coreHeuristic;
    private final boolean symmetry;
    private final boolean onSwitch;
    private final boolean offSwitch;
    private final ApplicationMode appMode;

    private HashMap<ReferenceBoard, ReferenceMoves> referenceMap;
    private HashMap<ReferenceBoard, ReferenceMoves> defaultMap;
    private int cutoffSetting;
    private double cutoffLimit;
    private boolean fileReady = false;
    private SmartSolverPdb localSolver = null;

    /**
     * Initializes ReferenceAccumulator object.  Load the stored collection from file.
     * Use default setting if not available.
     *
     * @throws RemoteException throw exception when connection lost
     */
    public ReferenceAccumulator() throws RemoteException {
        this(ApplicationMode.CONSOLE);
    }

    /**
     * Initializes ReferenceAccumulator object.  Load the stored collection from file.
     * Use default setting if not available.
     *
     * @param appMode the given application mode for Gui or Console.
     * @throws RemoteException throw exception when connection lost
     */
    public ReferenceAccumulator(ApplicationMode appMode) throws RemoteException {
        directory = FileProperties.getDirectory();
        filepath = FileProperties.getFilepathReference();
        coreSolverClassName = ReferenceConstants.getCoreSolverClassName();
        coreHeuristic = ReferenceConstants.getCoreHeuristic();
        symmetry = ReferenceConstants.isSymmetry();
        onSwitch = SolverConstants.isOnSwitch();
        offSwitch = !onSwitch;
        this.appMode = appMode;

        try {
            referenceMap = new HashMap<ReferenceBoard, ReferenceMoves>();
            loadFile();
        } catch (IOException ex) {
            reset();
        }

        updateData(createSolver());
        refreshFile();
    }

    // load the default set
    void loadDefault() {
        defaultMap = new HashMap<ReferenceBoard, ReferenceMoves>();
        for (byte[][] preset : ReferenceProperties.getDefaultBoards()) {
            ReferenceBoard advBoard = new ReferenceBoard(new Board(preset[0]));
            ReferenceMoves advMoves = new ReferenceMoves(preset[1][0], preset[1][1]);
            defaultMap.put(advBoard, advMoves);
        }
    }

    // clear the default set
    void clearDefault() {
        defaultMap =  null;
    }

    // reset to default setting
    void reset() {
        cutoffSetting = ReferenceProperties.getDefaultCutoffLimit();
        System.out.println("Default setting : cutoff archive limit - "
                + cutoffSetting);
        int cutoffBuffer = ReferenceProperties.getCutoffBuffer();
        cutoffLimit = cutoffSetting * ((100 - cutoffBuffer) / 100.0);

        loadDefault();
        referenceMap = new HashMap<ReferenceBoard, ReferenceMoves>();
        for (Entry<ReferenceBoard, ReferenceMoves> entry : defaultMap.entrySet()) {
            referenceMap.put(entry.getKey(), entry.getValue());
        }
        clearDefault();
    }

    /**
     * Returns a HashMap of collection of reference boards.
     *
     * @return HashMap of collection of reference boards
     */
    public final HashMap<ReferenceBoard, ReferenceMoves> getActiveMap() {
        if (referenceMap == null) {
            reset();
        }
        return referenceMap;
    }

    /**
     * Returns an integer of cutoff setting.
     *
     * @return integer of cutoff setting
     */
    public int getCutoffSetting() {
        return cutoffSetting;
    }

    /**
     * Returns a double of cutoff limit (95% of cutoff setting).
     *
     * @return double of cutoff limit
     */
    public double getCutoffLimit() {
        return cutoffLimit;
    }

    // load the reference collection from file
    private void loadFile() throws IOException {
        FileInputStream fin = new FileInputStream(filepath);
        FileChannel inChannel = fin.getChannel();
        ByteBuffer buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());

        cutoffSetting = buffer.getInt();
        int cutoffBuffer = ReferenceProperties.getCutoffBuffer();
        cutoffLimit = cutoffSetting * ((100 - cutoffBuffer) / 100.0);

        while (buffer.remaining() >= 34) {
            ReferenceBoard advBoard = null;

            long transformKey = buffer.getLong();
            byte group = buffer.get();
            int hash1 = buffer.getInt();
            int hash2 = buffer.getInt();
            int hashcode = buffer.getInt();
            advBoard = new ReferenceBoard(transformKey, group, hash1, hash2, hashcode);

            byte[] moves = new byte[4];
            buffer.get(moves);
            short[] initMoves = new short[4];
            for (int i = 0; i < 4; i++) {
                initMoves[i] = buffer.getShort();
            }
            byte status = buffer.get();
            if (referenceMap.containsKey(advBoard)) {
                ReferenceMoves advMoves = referenceMap.get(advBoard);
                advMoves.updateMoves(moves, initMoves, status);
            } else {
                ReferenceMoves advMoves = new ReferenceMoves(moves, initMoves, status);
                referenceMap.put(advBoard, advMoves);
            }
        }

        if (buffer.remaining() > 0) {
            fin.close();
            throw new IOException("Data file error - advanced_accumulator.db");
        }
        fin.close();
        fileReady = true;
    }

    // append a reference board with moves and partial solutions to file.
    private synchronized void add2file(ReferenceBoard advBoard, ReferenceMoves advMoves) {
        if (!fileReady) {
            System.err.println("System error : file system is not ready.");
            return;
        }
        if (!(new File(filepath)).exists()) {
            System.err.println("System error : " + filepath + " is missing.");
            return;
        }

        FileOutputStream fout;
        FileChannel outChannel;
        try {
            fout = new FileOutputStream(filepath, true);
            outChannel = fout.getChannel();

            ByteBuffer buffer = ByteBuffer.allocateDirect(34);
            long key = 0L;
            for (int val : advBoard.tilesTransform) {
                key <<= 4;
                key |= val;
            }
            buffer.putLong(key);                        //  8
            buffer.put(advBoard.group);                 //  1
            buffer.putInt(advBoard.hash1);              //  4
            buffer.putInt(advBoard.hash2);              //  4
            buffer.putInt(advBoard.hashcode);           //  4
            buffer.put(advMoves.moves);                 //  4
            for (short move : advMoves.initMoves) {     //  8 (2x4)
                buffer.putShort(move);
            }
            buffer.put(advMoves.status);                //  1
            buffer.flip();
            outChannel.write(buffer);

            outChannel.close();
            fout.close();
        } catch (IOException ex) {
            System.err.println("System error : write file error - " + filepath);
        }
    }

    // append a reference board with moves and partial solutions to file.
    private synchronized void add2file(ReferenceBoard advBoard, ReferenceMoves advMoves,
            String tempFile) {
        if (!(new File(tempFile)).exists()) {
            System.err.println("System error : " + tempFile + " is missing.");
            return;
        }

        FileOutputStream fout;
        FileChannel outChannel;
        try {
            fout = new FileOutputStream(tempFile, true);
            outChannel = fout.getChannel();

            ByteBuffer buffer = ByteBuffer.allocateDirect(34);
            long key = 0L;
            for (int val : advBoard.tilesTransform) {
                key <<= 4;
                key |= val;
            }
            buffer.putLong(key);                        //  8
            buffer.put(advBoard.group);                 //  1
            buffer.putInt(advBoard.hash1);              //  4
            buffer.putInt(advBoard.hash2);              //  4
            buffer.putInt(advBoard.hashcode);           //  4
            buffer.put(advMoves.moves);                 //  4
            for (short move : advMoves.initMoves) {     //  8 (2x4)
                buffer.putShort(move);
            }
            buffer.put(advMoves.status);                //  1
            buffer.flip();
            outChannel.write(buffer);

            outChannel.close();
            fout.close();
        } catch (IOException ex) {
            System.err.println("System error : write file error - " + tempFile);
        }
    }

    // create and return a SmartSolverPD object.
    SmartSolverPdb createSolver() {
        try {
            SmartSolverPdb solver = new SmartSolverPdb(PatternOptions.Pattern_78,
                    new ReferenceAdapter(this), appMode);
            solver.messageSwitch(offSwitch);
            solver.timeoutSwitch(offSwitch);
            solver.versionSwitch(onSwitch);
            return solver;
        } catch (OutOfMemoryError | RemoteException ex) {
            return null;
        }
    }

    /**
     * Returns the local SmartSolverPdb for Gui application only. Otherwise, return null.
     */
    public SmartSolverPdb getSolver() {
        if (appMode == ApplicationMode.GUI && validateSolver(localSolver)) {
            return localSolver;
        }
        return null;
    }

    /**
     * Returns the boolean value of the given solver is the valid for ReferenceAccumulator.
     *
     * @param inSolver the giver concrete instance of Solver interface.
     * @return boolean value of the given solver is the valid for ReferenceAccumulator
     */
    boolean validateSolver(SmartSolver inSolver) {
        if (inSolver == null) {
            return false;
        }
        if (!inSolver.getClass().getSimpleName().equals(coreSolverClassName)) {
            return false;
        }
        if (inSolver.getHeuristicOptions() != coreHeuristic) {
            return false;
        }
        return true;
    }

    // verify the given solver is using pattern database 7-8, scan the full
    // collection, if the reference board is not verified, verify it now.
    void updateData(SmartSolver inSolver) throws RemoteException {
        SmartSolver solver = inSolver;
        if (!validateSolver(inSolver)) {
            solver = createSolver();
        }

        if (solver == null) {
            return;
        }
        updateAll((SmartSolverPdb) solver);
    }

    /**
     * Verify the given solver is using pattern database 7-8, scan the full
     * collection, if the reference board is not verified, verify it now.
     *
     * @param inSolver the SolverInterface object in use
     * @throws RemoteException throw exception when connection lost
     */
    public void updatePending(SmartSolver inSolver) throws RemoteException {
        if (!validateSolver(inSolver)) {
            return;
        }
        updateAll((SmartSolverPdb) inSolver);
    }

    /**
     * Verify the given solver is using pattern database 7-8, scan the full
     * collection, if the reference board is not verified, verify it now.
     *
     * @throws RemoteException throw exception when connection lost
     */
    public void updatePending() throws RemoteException {
        if (localSolver == null) {
            localSolver = createSolver();
        }
        if (localSolver == null) {
            return;
        }
        updateAll(localSolver);
    }

    // scan the full collection, if the reference board is not verified, verify it now.
    private void updateAll(SmartSolverPdb solverPdb78) throws RemoteException {
        assert solverPdb78 instanceof SmartSolverPdb : "updateAll without SmartSolverPD object";
        final boolean backupAdvPriority = solverPdb78.getInUseVersionFlag();
        final boolean backupMessageFlag = solverPdb78.getMessageFlag();
        final boolean backupTimeoutFlag = solverPdb78.getTimeoutFlag();
        solverPdb78.timeoutSwitch(offSwitch);
        solverPdb78.messageSwitch(offSwitch);
        solverPdb78.versionSwitch(onSwitch);

        for (Entry<ReferenceBoard, ReferenceMoves> entry : referenceMap.entrySet()) {
            ReferenceMoves advMoves = entry.getValue();
            if (advMoves.isCompleted()) {
                continue;
            }
            ReferenceBoard advBoard = entry.getKey();
            advMoves.updateSolutions(advBoard, solverPdb78);
            add2file(advBoard, advMoves);
        }

        solverPdb78.versionSwitch(backupAdvPriority);
        solverPdb78.messageSwitch(backupMessageFlag);
        solverPdb78.timeoutSwitch(backupTimeoutFlag);
    }

    /**
     * If the given solver using pattern database 7-8, and it takes
     * over the cutoff limit solve the puzzle with advanced estimate;
     * add to reference boards collection.
     *
     * @param inSolver the SolverInterface object in use
     * @throws RemoteException throw exception when connection lost
     */
    public boolean addBoard(SmartSolver inSolver) throws RemoteException {
        return addBoard(inSolver, false);
    }

    // add a reference board in collection, allow bypass minimum
    // cutoff limit requirement
    boolean addBoard(SmartSolver inSolver, boolean bypass) throws RemoteException {
        if (referenceMap == null) {
            return false;
        }
        if (!validateSolver(inSolver)) {
            return false;
        }
        if (!bypass && inSolver.searchTime() < getCutoffLimit()) {
            return false;
        }

        SmartSolverPdb solverPdb78 = (SmartSolverPdb) inSolver;
        Board board = solverPdb78.lastSearchBoard();
        Direction[] solution = solverPdb78.solution().clone();

        if (!bypass && !solverPdb78.getInUseVersionFlag()) {
            int heuristicOrg = solverPdb78.heuristicStandard(board);
            int heuristicAdv = solverPdb78.heuristicAdvanced(board);
            if (heuristicOrg != heuristicAdv) {
                return false;
            }
        }

        final boolean backupAdvPriority = solverPdb78.getInUseVersionFlag();
        final boolean backupMessageFlag = solverPdb78.getMessageFlag();
        final boolean backupTimeoutFlag = solverPdb78.getTimeoutFlag();
        solverPdb78.timeoutSwitch(offSwitch);
        solverPdb78.messageSwitch(offSwitch);
        solverPdb78.versionSwitch(onSwitch);

        ReferenceBoard advBoard = new ReferenceBoard(board);
        byte lookup = ReferenceConstants.getReferenceLookup(board.getZero1d());
        int group = ReferenceConstants.getReferenceGroup(board.getZero1d());

        if (referenceMap.containsKey(advBoard)) {
            ReferenceMoves advMoves = referenceMap.get(advBoard);
            if (group == 3) {
                advMoves.updateSolution(lookup, solverPdb78.moves(), solution, symmetry);
            } else {
                advMoves.updateSolution(lookup, solverPdb78.moves(), solution, !symmetry);
            }
            if (bypass && !advMoves.isCompleted()) {
                advMoves.updateSolutions(advBoard, solverPdb78);
            }
            add2file(advBoard, advMoves);

            inSolver.versionSwitch(backupAdvPriority);
            inSolver.messageSwitch(backupMessageFlag);
            inSolver.timeoutSwitch(backupTimeoutFlag);
            return true;
        }

        ReferenceBoard advBoardSym = null;
        if (group == 0 || group == 2) {
            advBoardSym = new ReferenceBoard(new Board(board.getTilesSym()));
        }

        if (referenceMap.containsKey(advBoardSym)) {
            ReferenceMoves advMoves = referenceMap.get(advBoardSym);
            if (lookup == 1) {
                lookup = 3;
            } else if (lookup == 3) {
                lookup = 1;
            }
            advMoves.updateSolution(lookup, solverPdb78.moves(), solution, symmetry);
            if (bypass && !advMoves.isCompleted()) {
                advMoves.updateSolutions(advBoardSym, solverPdb78);
            }
            add2file(advBoardSym, advMoves);

            inSolver.versionSwitch(backupAdvPriority);
            inSolver.messageSwitch(backupMessageFlag);
            inSolver.timeoutSwitch(backupTimeoutFlag);
            return true;
        }

        ReferenceMoves advMoves = new ReferenceMoves(board.getZero1d(), solverPdb78.moves());
        if (group == 3) {
            advMoves.updateSolution(lookup, solverPdb78.moves(), solution, symmetry);
        } else {
            advMoves.updateSolution(lookup, solverPdb78.moves(), solution, !symmetry);
        }
        if (bypass && !advMoves.isCompleted()) {
            advMoves.updateSolutions(advBoard, solverPdb78);
        }
        referenceMap.put(advBoard, advMoves);
        add2file(advBoard, advMoves);

        inSolver.versionSwitch(backupAdvPriority);
        inSolver.messageSwitch(backupMessageFlag);
        inSolver.timeoutSwitch(backupTimeoutFlag);
        return true;
    }

    /**
     * If the given solver using pattern database 7-8, and it takes
     * over the cutoff limit solve the puzzle with advanced estimate;
     * add to reference boards collection.
     *
     * @param board the given board object
     * @param steps the byte value of number of moves
     * @param solution the Direction array of moves
     */
    @Override
    public boolean addBoard(Board board, byte steps, Direction[] solution) {
        if (referenceMap == null) {
            return false;
        }
        if (localSolver == null) {
            localSolver = createSolver();
        }
        if (localSolver == null) {
            return false;
        }

        ReferenceBoard advBoard = new ReferenceBoard(board);
        byte lookup = ReferenceConstants.getReferenceLookup(board.getZero1d());
        int group = ReferenceConstants.getReferenceGroup(board.getZero1d());

        if (referenceMap.containsKey(advBoard)) {
            ReferenceMoves advMoves = referenceMap.get(advBoard);
            if (group == 3) {
                advMoves.updateSolution(lookup, steps, solution, symmetry);
            } else {
                advMoves.updateSolution(lookup, steps, solution, !symmetry);
            }
            add2file(advBoard, advMoves);
            return true;
        }

        ReferenceBoard advBoardSym = null;
        if (group == 0 || group == 2) {
            advBoardSym = new ReferenceBoard(new Board(board.getTilesSym()));
        }

        if (referenceMap.containsKey(advBoardSym)) {
            ReferenceMoves advMoves = referenceMap.get(advBoardSym);
            if (lookup == 1) {
                lookup = 3;
            } else if (lookup == 3) {
                lookup = 1;
            }
            advMoves.updateSolution(lookup, steps, solution, symmetry);
            add2file(advBoardSym, advMoves);
            return true;
        }

        ReferenceMoves advMoves = new ReferenceMoves(board.getZero1d(), steps);
        if (group == 3) {
            advMoves.updateSolution(lookup, steps, solution, symmetry);
        } else {
            advMoves.updateSolution(lookup, steps, solution, !symmetry);
        }
        referenceMap.put(advBoard, advMoves);
        add2file(advBoard, advMoves);
        return true;
    }

    @Override
    public boolean addBoard(Board board, byte steps, Direction[] solution, SmartSolver inSolver) {
        // do nothing, should not be called
        return false;
    }

    /**
     * If the solver is SolverPD object and last search board in activeMap
     * that need to verify; verify the full set and return true.
     *
     * @param inSolver the given SolverIntegerface
     * @return boolean if last search board in activeMap has been verified.
     * @throws RemoteException throw exception when connection lost
     */
    public boolean updateLastSearch(SmartSolver inSolver) throws RemoteException {
        if (referenceMap == null) {
            return false;
        }

        if (!validateSolver(inSolver)) {
            return false;
        }

        SmartSolverPdb solverPdb78 = (SmartSolverPdb) inSolver;

        final boolean backupAdvPriority = solverPdb78.getInUseVersionFlag();
        final boolean backupMessageFlag = solverPdb78.getMessageFlag();
        final boolean backupTimeoutFlag = solverPdb78.getTimeoutFlag();
        solverPdb78.timeoutSwitch(offSwitch);
        solverPdb78.messageSwitch(offSwitch);
        solverPdb78.versionSwitch(onSwitch);

        Board board = solverPdb78.lastSearchBoard();
        ReferenceBoard advBoard = new ReferenceBoard(board);
        int group = ReferenceConstants.getReferenceGroup(board.getZero1d());

        if (referenceMap.containsKey(advBoard)) {
            ReferenceMoves advMoves = referenceMap.get(advBoard);
            if (!advMoves.isCompleted()) {
                System.out.println("System update, please wait.");
                advMoves.updateSolutions(advBoard, solverPdb78);
                add2file(advBoard, advMoves);
            }

            inSolver.versionSwitch(backupAdvPriority);
            inSolver.messageSwitch(backupMessageFlag);
            inSolver.timeoutSwitch(backupTimeoutFlag);
            return true;
        }

        ReferenceBoard advBoardSym = null;
        if (group == 0 || group == 2) {
            advBoardSym = new ReferenceBoard(new Board(board.getTilesSym()));
        }

        if (referenceMap.containsKey(advBoardSym)) {
            ReferenceMoves advMoves = referenceMap.get(advBoardSym);
            if (!advMoves.isCompleted()) {
                System.out.println("System update, please wait.");
                advMoves.updateSolutions(advBoardSym, solverPdb78);
                add2file(advBoardSym, advMoves);
            }

            inSolver.versionSwitch(backupAdvPriority);
            inSolver.messageSwitch(backupMessageFlag);
            inSolver.timeoutSwitch(backupTimeoutFlag);
            return true;
        }

        inSolver.versionSwitch(backupAdvPriority);
        inSolver.messageSwitch(backupMessageFlag);
        inSolver.timeoutSwitch(backupTimeoutFlag);
        return false;
    }

    /**
     * If the solver is SolverPD object and last search board in activeMap
     * that need to verify; verify the full set and return true.
     *
     * @param board the given Board object
     * @return boolean if last search board in activeMap has been verified.
     * @throws RemoteException throw exception when connection lost
     */
    public boolean updateLastSearch(Board board) throws RemoteException {
        if (referenceMap == null) {
            return false;
        }
        if (localSolver == null) {
            localSolver = createSolver();
        }
        if (localSolver == null) {
            return false;
        }

        ReferenceBoard advBoard = new ReferenceBoard(board);
        int group = ReferenceConstants.getReferenceGroup(board.getZero1d());

        if (referenceMap.containsKey(advBoard)) {
            ReferenceMoves advMoves = referenceMap.get(advBoard);
            if (!advMoves.isCompleted()) {
                System.out.println("System update, please wait.");
                advMoves.updateSolutions(advBoard, localSolver);
                add2file(advBoard, advMoves);
            }
            return true;
        }

        ReferenceBoard advBoardSym = null;
        if (group == 0 || group == 2) {
            advBoardSym = new ReferenceBoard(new Board(board.getTilesSym()));
        }

        if (referenceMap.containsKey(advBoardSym)) {
            ReferenceMoves advMoves = referenceMap.get(advBoardSym);
            if (!advMoves.isCompleted()) {
                System.out.println("System update, please wait.");
                advMoves.updateSolutions(advBoardSym, localSolver);
                add2file(advBoardSym, advMoves);
            }
            return true;
        }
        return false;
    }

    public boolean updateLastSearch(Board board, SmartSolver inSolver) {
        // do nothing, should not be called
        return false;
    }

    // remove the given board from reference boards collection if exists, except
    // default reference boards.
    void removeBoard(Board board) {
        if (defaultMap == null) {
            loadDefault();
        }
        ReferenceBoard advBoard = new ReferenceBoard(board);
        if (defaultMap.containsKey(advBoard)) {
            return;
        }

        if (referenceMap.containsKey(advBoard)) {
            referenceMap.remove(advBoard);
        }
    }

    // print the current status of reference boards collection.
    void printStatus() {
        System.out.println("Data file size: " + (new File(filepath).length())
                + " saved at " + new Date((new File(filepath)).lastModified()));
        System.out.println("Boards takes over " + cutoffSetting + "s will store in file.");
        System.out.println(referenceMap.size() + " of boards stored in data file.");
        System.out.println("The cutoff limit with buffer: " + cutoffLimit + "\n");
    }

    // print all reference boards and it's components.
    void printAllBoards() {
        int count = 1;
        for (Entry<ReferenceBoard, ReferenceMoves> entry : referenceMap.entrySet()) {
            System.out.println(count++ + " : ");
            System.out.println(entry.getKey());
            System.out.println(entry.getValue());
        }
        System.out.println();
    }

    // change the cutoff setting with the given integer in second, range from 1 to 10 and
    // save a new copy if stand alone or off network
    void setCutoffArchive(int cutoff) {
        if (cutoff < 1) {
            System.out.println(cutoff + " below minimum cutoff limit 1.0s - stop and no change");
            return;
        } else if (cutoff > 10) {
            System.out.println(cutoff + " above maximum cutoff limit 10.0s - stop and no change");
            return;
        } else if (cutoff == cutoffSetting) {
            System.out.println("Same cutoff limit - no change.");
            return;
        }

        cutoffSetting = cutoff;
        int cutoffBuffer = ReferenceProperties.getCutoffBuffer();
        cutoffLimit = cutoffSetting * ((100 - cutoffBuffer) / 100.0);

        System.out.println("Cutoff archive limit changed to " + cutoffSetting
                + " seconds, existing archive boards will remain as is.");
        refreshFile();
    }

    // create the new file for storing the reference collection.
    private String createFile() {
        if (!(new File(directory)).exists()) {
            (new File(directory)).mkdir();
        }
        int count = 0;
        String tempFile = filepath + "temp" + count;
        while ((new File(tempFile)).exists()) {
            tempFile = filepath + "temp" + (++count);
        }

        FileOutputStream fout;
        FileChannel outChannel;
        ByteBuffer buffer;

        try {
            fout = new FileOutputStream(tempFile);
            outChannel = fout.getChannel();
            buffer = ByteBuffer.allocateDirect(8);
            buffer.putInt(cutoffSetting);
            buffer.flip();
            outChannel.write(buffer);
            outChannel.close();
            fout.close();
            return tempFile;
        } catch (IOException ex) {
            System.err.println("System error : unable to save file.");
            return null;
        }
    }

    // save all reference board in a new copy
    synchronized void refreshFile() {
        String tempFile = createFile();
        if (tempFile == null) {
            return;
        }

        for (Entry<ReferenceBoard, ReferenceMoves> entry : referenceMap.entrySet()) {
            add2file(entry.getKey(), entry.getValue(), tempFile);
        }

        fileReady = false;
        (new File(tempFile)).renameTo(new File(filepath));
        fileReady = true;
    }
}
