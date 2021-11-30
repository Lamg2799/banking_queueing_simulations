package com.sim.proj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import org.apache.commons.math3.distribution.PoissonDistribution;

/**
 * Multi server implementaion as a part of our project for CSI4124 Simulation
 * and modelisation Created by:
 * 
 * @author Samuel Garneau, nov 19th, 2021, Ottawa, On
 * @version 1.0
 */
public class Multiserver {
    public static final String TEXT_RESET = "\u001B[0m";
    public static final String TEXT_GREEN = "\u001B[32m";
    /**
     * List of customers to go through simulation
     */
    private ArrayList<Customer> customers = null;

    private int numServer = 0;

    /**
     * Stores the max number of execution of the simulation
     */
    private int numMaxLoop = 0;

    /**
     * Stores the current loop execution
     */
    private int currentLoop = 1;

    private int numCustomersServed = 0;
    private int numCustomersArrived = 0;

    /**
     * Stores the event clock for the current simulation
     */
    private double clock = 0;
    private double totalClock = 0;

    /**
     * Stores the max waiting time for all customers and simulations
     */
    private double maxWaitingTime = 0;

    /**
     * Stores the total time the server was busy for all customers and simulations
     */
    private double[] totalServerTime;

    /**
     * Stores the desired service time mean for the simulations
     */
    private double meanS = 0;

    /**
     * Stores the desired service time mean standart variation for the simulations
     */
    private double sigmaS = 0;

    /**
     * Stores the single server status either IDLE or BUSY
     */
    private State[] serverStatus;

    /**
     * Stores the customers waiting in line for service during a simulation
     */
    private CustomerQueue customersQ = null;

    /**
     * Stores the current event being processed
     */
    private Event event = null;

    /**
     * Stores the list of all future events for a simulation auto-sorted based on
     * event time
     */
    private LinkedList<Event> eventList = null;

    private Random rdm = new Random();
    /**
     * Stores the random generator for the service time for all simulations
     */
    private Random rdmS = null;

    /**
     * Stores the start time clock to compute execution time in millis
     */

    private double maxClock = 0;

    private double meanDivider = 2.0; // default value

    private Results results = null;
    private final double SERVER_RATE = 320.0;

    public Multiserver() {

    }

    /**
     * Run simulation method
     */
    public Results runSim(String[] args) {
        // retrieve config and create customers
        initialize(args);

        // Starts simulation
        System.out.println();
        System.out.print(
                TEXT_GREEN + "Running " + numMaxLoop + " multiserver simulations with " + args[4] + " servers...");

        // Loops to run multiple simulations
        while (currentLoop <= numMaxLoop) {

            // reinitialize all variables
            reInitialize();

            // loop through events in chronological order and process the right event type
            // stops when the workday is over ( 8hr)
            while (clock < maxClock) {

                // retrieve the next event
                event = eventList.removeFirst();
                // update the clock to current event
                clock = event.getTime();

                // Calls the appropriate method depending on event type
                switch (event.getType()) {
                    case DEPARTURE:
                        processDeparture();
                        break;
                    case ARRIVAL:
                        processArrival();
                        break;

                }
            }

            // record stats for customers who did not get served during work day
            while (!customersQ.isEmpty()) {
                var c = customersQ.dequeue();
                c.getCustomer().setWaitingTime(clock);
                c.getCustomer().setTotalSystemTime(clock);
            }

            // compute avg results for this execution
            storeExecutionResults();

            // updates clocks and loops variables
            totalClock += clock;
            currentLoop++;
        }

        System.out.println("Done" + TEXT_RESET);

        // Generate the outputs

        return storeResults();
    }

    /**
     * Retrieves the configs initialyze objects
     */
    private void initialize(String[] args) {

        // initalize parameters variables
        numMaxLoop = Integer.parseInt(args[0]);
        maxClock = Double.parseDouble(args[1]);
        meanS = Integer.parseInt(args[2]);
        sigmaS = Integer.parseInt(args[3]);
        numServer = Integer.parseInt(args[4]);
        meanDivider = Double.parseDouble(args[5]);
        numCustomersServed = 0;
        rdmS = new Random();
        totalServerTime = new double[numServer];
        serverStatus = new State[numServer];
        customersQ = new CustomerQueue();
        results = new Results();
    }

    /**
     * Reset all the variable before starting a new simulation
     */
    private void reInitialize() {

        // reinitializing variables
        clock = 0;
        eventList = new LinkedList<Event>();
        customers = new ArrayList<Customer>();

        for (int i = 0; i < serverStatus.length; i++) {

            serverStatus[i] = State.IDLE;
        }

        // generating InterArrival Events depending max time (8hr)
        double nextIA = 0;
        double currentIA = 0;

        while (currentIA < maxClock) {
            // generate next random value based on poisson distribution with mean based on
            // quadratic equation

            nextIA = generateNextIA(currentIA);

            currentIA += nextIA;
            Customer c = new Customer();

            customers.add(c);

            c.setInterArrivalValue(nextIA);
            // creates arrival event
            eventList.add(new Event(EventType.ARRIVAL, currentIA, c));

        }

        // sort event list
        sort();
    }

    /**
     * generate next interarrival time based on poisson distribution and time of day
     * function
     * 
     * @param time
     * @return
     */
    private double generateNextIA(double time) {

        var mean = (Math.pow(time, 2) * 0.000003657) - (0.1262 * time) + 1200;

        // System.out.println("Poisson Distribution");

        var pd = new PoissonDistribution(mean / meanDivider);
        var ret = pd.sample();
        // System.out.println("Random value " + ret);

        return ret;
    }

    /**
     * If the server is IDLE then it will create an DEPARTURE event, otherwise it
     * will enqueue the event in the customers queue
     */
    private void processArrival() {
        numCustomersArrived++;
        var c = event.getCustomer();
        // record customer arrival time
        c.setArrivalTime(clock);
        // check if any server idle
        Map<Integer, Integer> idleServers = new HashMap<Integer, Integer>();
        var s = serverStatus;
        for (int i = 0; i < s.length; i++) {

            if (s[i] == State.IDLE) {

                idleServers.put(i, i);
            }

        }
        // System.out.println("Processing arrival - number of servers at idle " +
        // idleServers.size());

        if (!idleServers.isEmpty()) {
            // System.out.println("Processing arrival idleServers.contains(s.length - 1) "
            // + idleServers.containsKey(s.length - 1) + " " + idleServers.size());
            int index = 0;

            if (idleServers.containsKey(s.length - 1) && idleServers.size() > 1) {
                idleServers.remove(s.length - 1);
            }
            var indexpick = 0;
            if (idleServers.size() > 1) {

                indexpick = rdm.nextInt(idleServers.size());

            }
            var keyset = idleServers.keySet();
            var arr = keyset.toArray();
            index = (Integer) arr[indexpick];
            // set server busy
            serverStatus[index] = State.BUSY;
            // System.out.println("Processing arrival customer id " + c.getId() + " at " +
            // clock[currentLoop] + " server #: " + index);
            c.setServerIndex(index);
            // schedule departure event

            double nextS = Math.abs(rdmS.nextGaussian() * sigmaS + meanS);
            totalServerTime[index] += (nextS);
            eventList.add(new Event(EventType.DEPARTURE, nextS + event.getTime(), c));
            sort();

        } else {
            // System.out.println("Processing arrival customer id " + c.getId() + " at " +
            // clock + " enqueued");
            customersQ.enqueue(event);
        }

    }

    /**
     * Records departure time, if any customer is waiting then create new departure
     * event and record waiting time, otherwise set the server status to IDLE and
     * record server busy time
     */
    private void processDeparture() {

        numCustomersServed++;
        var c = event.getCustomer();
        int serverIndex = c.getServerIndex();
        // System.out.println("Processing depart customer id " + c.getId() + " at " +
        // clock + " server # " + serverIndex);
        // record customer system time
        c.setTotalSystemTime(clock);

        // check if any customer are waiting in line
        if (customersQ.isEmpty()) {
            // update server status and record server busy time
            serverStatus[serverIndex] = State.IDLE;

        } else {

            // process customer waiting in line
            event = customersQ.dequeue();
            c = event.getCustomer();

            // record customer waiting time
            c.setWaitingTime(clock);

            c.setServerIndex(serverIndex);
            // record the max waiting time
            if (c.getWaitingTime() > maxWaitingTime) {
                maxWaitingTime = c.getWaitingTime();
            }

            // schedule departure event

            double nextS = Math.abs(rdmS.nextGaussian() * sigmaS + meanS);
            totalServerTime[serverIndex] += (nextS);
            eventList.add(new Event(EventType.DEPARTURE, nextS + clock, c));
            sort();

        }

    }

    /**
     * Sorts the event list in order of event time
     */
    private void sort() {

        // Create new comparator to compare event time
        Comparator<Event> compareByTime = new Comparator<Event>() {
            @Override
            public int compare(Event e1, Event e2) {
                return e1.getTime().compareTo(e2.getTime());
            }

        };

        Collections.sort(eventList, compareByTime);
    }

    private void storeExecutionResults() {
        // compute avg waiting and system time
        var waitingAcc = 0.0;
        var systemAcc = 0.0;

        for (Customer customer : customers) {
            waitingAcc += customer.getWaitingTime();
            systemAcc += customer.getTotalSystemTime();
        }
        var waitingTimeAvg = waitingAcc / (double) customers.size();
        var systemTimeAvg = systemAcc / (double) customers.size();

        // compute variance
        var waitingVarAcc = 0.0;
        var systemVarAcc = 0.0;
        for (Customer customer : customers) {
            waitingVarAcc += Math.pow(customer.getWaitingTime() - waitingTimeAvg, 2);
            systemVarAcc += Math.pow(customer.getTotalSystemTime() - systemTimeAvg, 2);
        }
        var waitingTimeAvgVar = waitingVarAcc / (double) customers.size();
        var systemTimeAvgVar = systemVarAcc / (double) customers.size();

        // store in results

        results.addWaitingTime(waitingAcc);
        results.addSystemTime(systemAcc);
        results.addWaitingTimeAvgVar(waitingTimeAvgVar);
        results.addSystemTimeAvgVar(systemTimeAvgVar);
    }

    private Results storeResults() {

        double servers = (double) numServer;
        double maxloop = (double) numMaxLoop;
        double numcust = (double) numCustomersServed / maxloop;

        var avgStats = getExecutionsStats(); // [0]=waitingTime;[1]=waitingTimeVar;[2]avgWaitingTime;[3]avgWaitingTimeVar;[4]=systemTime;[5]=systemTimeVar;[6]avgSystemTime;[7]avgSystemTimeVar;[8]confidenceInterval

        results.addResult("custArrived", (double) numCustomersArrived / maxloop);
        results.addResult("maxQueue", (double) customersQ.getMaxQS());
        results.addResult("custServed", numcust);
        results.addResult("numServers", servers);
        results.addResult("loopDone", maxloop);
        results.addResult("typeServers", 1.0);
        results.addResult("totalCost", (double) numServer * SERVER_RATE);
        results.addResult("costPerCustomer", servers * SERVER_RATE / numcust);
        results.addResult("finalClock", totalClock / maxloop);
        results.addResult("waitingTime", avgStats[0]); // avg per executions
        results.addResult("waitingTimeVar", avgStats[1]); // avg variance per executions
        results.addResult("avgWaitingTime", avgStats[2]); // avg per execution per customers
        results.addResult("avgWaitingTimeVar", avgStats[3]);// avg variance per executions per cutomers
        results.addResult("maxWaitingTime", maxWaitingTime);
        results.addResult("systemTime", avgStats[4]);
        results.addResult("systemTimeVar", avgStats[5]);
        results.addResult("avgSystemTime", avgStats[6]);
        results.addResult("avgSystemTimeVar", avgStats[7]);
        results.addResult("waitingTimeH", avgStats[8]);
        results.addResult("systemTimeH", avgStats[9]);
        results.addResult("meanDivider", meanDivider);

        // compute server busy pourcentage
        for (int i = 0; i < numServer; i++) {

            var key = "timeServer" + i;
            var value = totalServerTime[i] / maxloop;
            results.addResult(key, value);
            value = 100 * totalServerTime[i] / totalClock;
            results.addResult(key + "%", value);

        }

        return results;
        // adds the results to output class so its available for comparing

    }

    private double[] getExecutionsStats() {
        var maxloop = (double) numMaxLoop;
        var cust = (double) numCustomersArrived / maxloop;
        double[] ret = new double[10];
        // init array
        for (int i = 0; i < ret.length; i++) {
            ret[i] = 0.0;
        }

        // compute total waiting time
        for (double avgwait : results.getWaitingTime()) {
            ret[0] += avgwait;
        }
        ret[0] = ret[0] / maxloop;

        // compute total waiting time variance
        for (double avgwait : results.getWaitingTime()) {
            ret[1] += Math.pow(avgwait - ret[0], 2);
        }
        ret[1] = ret[1] / (maxloop - 1);

        // compute avg waiting time and variance per customers
        ret[2] = ret[0] / cust;
        ret[3] = ret[1] / cust;

        // compute total system time
        for (double avgsyst : results.getSystemTime()) {
            ret[4] += avgsyst;
        }
        ret[4] = ret[4] / maxloop;

        // compute total system time variance
        for (double avgsyst : results.getSystemTime()) {
            
            ret[5] += Math.pow(avgsyst - ret[4], 2);
        }
        ret[5] =  ret[5] / (maxloop - 1);

        // compute avg system time and variance per customers
        ret[6] = ret[4] / cust;
        ret[7] = ret[5] / cust;

        // compute confidence interval
        var z = 0.7088; // to be validated from normal table
        ret[8] = z * Math.sqrt(ret[1]) / Math.sqrt(maxloop);
        ret[9] = z * Math.sqrt(ret[5]) / Math.sqrt(maxloop);

        return ret;
    }

}
