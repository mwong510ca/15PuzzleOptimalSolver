package mwong.myprojects.fifteenpuzzle.solver.ai;

import mwong.myprojects.fifteenpuzzle.solver.SmartSolver;
import mwong.myprojects.fifteenpuzzle.solver.advanced.SmartSolverPdb;
import mwong.myprojects.fifteenpuzzle.solver.components.Board;
import mwong.myprojects.fifteenpuzzle.solver.components.Direction;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashMap;

/**
 * ReferenceRemote is the remote interface of stored board of reference collection.
 * It has the same set of functions as the Reference interface with remote feature.
 *
 * <p>Dependencies : SmartSolver.java
 *
 * @author Meisze Wong
 *         www.linkedin.com/pub/macy-wong/46/550/37b/
 */
public interface ReferenceRemote extends Remote {
    /**
     * Returns a HashMap of collection of reference boards.
     *
     * @return HashMap of collection of reference boards
     */
    HashMap<ReferenceBoard, ReferenceMoves> getActiveMap() throws RemoteException;

    /**
     * Returns an integer of cutoff setting.
     *
     * @return integer of cutoff setting
     */
    int getCutoffSetting() throws RemoteException;

    /**
     * Returns a double of cutoff limit (95% of cutoff setting).
     *
     * @return double of cutoff limit
     */
    double getCutoffLimit() throws RemoteException;

    /**
     *  Verify the given solver is using pattern database 7-8, scan the full
     *  collection, if the reference board is not verified, verify it now.
     */
    void updatePending() throws RemoteException;

    /**
     *  Verify the given solver is using pattern database 7-8, scan the full
     *  collection, if the reference board is not verified, verify it now.
     *
     *  @param inSolver the SolverInterface object in use
     */
    void updatePending(SmartSolver inSolver) throws RemoteException;

    public SmartSolverPdb getSolver() throws RemoteException;

    /**
     * If the given solver using pattern database 7-8, and it takes
     * over the cutoff limit solve the puzzle with advanced estimate;
     * add to reference boards collection.
     *
     * @param inSolver the SolverInterface object in use
     */
    public boolean addBoard(SmartSolver inSolver) throws RemoteException;

    /**
     * If the given solver using pattern database 7-8, and it takes
     * over the cutoff limit solve the puzzle with advanced estimate;
     * add to reference boards collection.
     *
     * @param board the given board object
     * @param steps the byte value of number of moves
     * @param solution the Direction array of moves
     */
    public boolean addBoard(Board board, byte steps, Direction[] solution) throws RemoteException;

    /**
     * If the given solver using pattern database 7-8, and it takes
     * over the cutoff limit solve the puzzle with advanced estimate;
     * add to reference boards collection.
     *
     * @param board the given board object
     * @param steps the byte value of number of moves
     * @param solution the Direction array of moves
     * @param inSolver the SolverInterface object in use
     */
    public boolean addBoard(Board board, byte steps, Direction[] solution, SmartSolver inSolver)
            throws RemoteException;

    /**
     * If the solver is SolverPD object and last search board in activeMap
     * that need to verify; verify the full set and return true.
     *
     * @param inSolver the given SolverIntegerface
     * @return boolean if last search board in activeMap has been verified.
     */
    public boolean updateLastSearch(SmartSolver inSolver) throws RemoteException;

    /**
     * If the solver is SolverPD object and last search board in activeMap
     * that need to verify; verify the full set and return true.
     *
     * @param board the given Board object
     * @return boolean if last search board in activeMap has been verified.
     */
    public boolean updateLastSearch(Board board) throws RemoteException;

    /**
     * If the solver is SolverPD object and last search board in activeMap
     * that need to verify; verify the full set and return true.
     *
     * @param board the given Board object
     * @param inSolver the given SolverIntegerface
     * @return boolean if last search board in activeMap has been verified.
     * @throws RemoteException throw exception when connection lost
     */
    public boolean updateLastSearch(Board board, SmartSolver inSolver) throws RemoteException;
}
