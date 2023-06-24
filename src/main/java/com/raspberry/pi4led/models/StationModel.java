package com.raspberry.pi4led.models;

import com.pi4j.io.gpio.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
@Getter
@Setter
public class StationModel {
    private final int startBitLength = 1;
    private final int stopBitLength = 1;
    private final int controllerLength = 2;
    private final int taskLength = 4;
    private final int messageLength = startBitLength+controllerLength+taskLength+stopBitLength;
    long frequencyTimer;

    private ArrayList<Integer> checkControllerMessages = new ArrayList<>(Arrays.asList(1, 33, 65, 97));
    private Integer checkControllerMessage;
    private ArrayList<Integer> executionErrorIds = new ArrayList<>(Arrays.asList(1, 2, 3, 4));
    private ArrayList<Integer> connectionErrorIds = new ArrayList<>(Arrays.asList(5, 6, 7, 8));
    private ArrayList<Integer> errors = new ArrayList<>(Arrays.asList(31, 63, 95, 127));
    private Integer errorId = 0;

    private State state;
    private State stateBeforeEmergency;
    private Control control;
    private int trainCounter;
    private int currentWay = 8;
    private String nameOfStation;

    private boolean isFirst = true;
    private boolean isBusy = false;
    private boolean isTryingToLoadPage = false;

    boolean isSending, isReceiving, isFalseMessage;
    private BitSet receivedMessage = new BitSet(8);
    private static GpioPinDigitalMultipurpose pin;
    private final GpioController gpioController = GpioFactory.getInstance();

    ArrayList<String> cities = new ArrayList<String>(Arrays.asList("Москва", "Казань", "Магадан", "Воркута", "Якутск", "Тюмень"));
    ArrayList<Integer> counters = new ArrayList<Integer>(Arrays.asList(0, 0, 0, 0, 0, 0));
    ArrayList<wagonModel> wagonList = new ArrayList<wagonModel>();
    public void setInput() {
        if (pin == null) {
            pin = gpioController.provisionDigitalMultipurposePin(RaspiPin.GPIO_01, PinMode.DIGITAL_INPUT);
        }
        if (pin.getMode() == PinMode.DIGITAL_OUTPUT) {
            pin.setMode(PinMode.DIGITAL_INPUT);
        }
    }
    public void setOutput() {
        if (pin == null) {
            pin = gpioController.provisionDigitalMultipurposePin(RaspiPin.GPIO_01, PinMode.DIGITAL_OUTPUT);
        }
        if (pin.getMode() == PinMode.DIGITAL_INPUT) {
            pin.setMode(PinMode.DIGITAL_OUTPUT);
        }
    }
    public BitSet convertToBitSet(Integer message) {
        BitSet resMessage = new BitSet(messageLength);
        int pos = 0;
        for (int i = 0; i!= messageLength-Integer.toBinaryString(message).length(); i++) {
            resMessage.clear(pos);
            pos++;
        }
        for(char bit : Integer.toBinaryString(message).toCharArray()) {
            if(bit == '1') {
                resMessage.set(pos);
            }
            else {
                resMessage.clear(pos);
            }
            pos++;
        }
        return resMessage;
    }

    public Integer convertReceived(BitSet bits) {
        int value = 0;
        for (int i = 0; i != messageLength; i++) {
            value += bits.get(i) ? (1 << 7-i) : 0;
        }
        return value;
    }

    public synchronized void sendMessage(Integer message) throws InterruptedException {
        BitSet messageBitSet = convertToBitSet(message);
        int j = 0;
        do { //repeat if didnt receive proper response
            setOutput();
            if(j > 0) {
                System.out.println("Couldnt recceive. Repeating.");
                long delay = System.currentTimeMillis();
                while (true) {
                    if (System.currentTimeMillis() - delay > 50)
                        break;
                }
            }
            frequencyTimer = System.currentTimeMillis();
            for (int i = 0; i!=messageLength; i++) {
                while (true) {
                    if (System.currentTimeMillis() - frequencyTimer >= 30) {
                        if (messageBitSet.get(i)) {
                            pin.high();
                            System.out.println(System.currentTimeMillis() - frequencyTimer);
                            System.out.println("Sent: " + messageBitSet.get(i));
                            frequencyTimer = System.currentTimeMillis();
                            break;
                        }
                        pin.low();
                        System.out.println(System.currentTimeMillis() - frequencyTimer);
                        System.out.println("Sent: " + messageBitSet.get(i));
                        frequencyTimer = System.currentTimeMillis();
                        break;
                    }
                }
            }
            setInput();
            if(!isFalseMessage) {
                receiveMessage();
            }
            j++;
        } while(j != 5
                && convertReceived(receivedMessage) == 0
                && !isFalseMessage);
//        if (j == 5) {
//            errorId = connectionErrorIds.get(checkControllerMessages.indexOf(checkControllerMessage));
//        }
    }
    public void receiveMessage() throws InterruptedException {
        receivedMessage.clear();
        long startTime = System.currentTimeMillis();
        System.out.println("After stop bit: " + (long)(startTime - frequencyTimer));
        while (true) {
            if(pin.isLow() || System.currentTimeMillis() - startTime > 1000) {
                break;
            }
        }
        if (pin.isHigh()) {
            return;
        }

        receivedMessage.clear(0);
        System.out.println(System.currentTimeMillis());
        System.out.println("Received: " + receivedMessage.get(0));
        frequencyTimer = System.currentTimeMillis() + 3;
        for (int i = 1; i != messageLength; i++) {
            while (true) {
                if (frequencyTimer < System.currentTimeMillis() && System.currentTimeMillis() - frequencyTimer >= 30) {
                    if (pin.isLow()) {
                        receivedMessage.clear(i);
                    } else {
                        receivedMessage.set(i);
                    }
                    System.out.println("Received: " + receivedMessage.get(i));
                    System.out.println(System.currentTimeMillis() - frequencyTimer);
                    frequencyTimer = System.currentTimeMillis();
                    break;
                }
            }
        }
        System.out.println("Whole message: " + convertReceived(receivedMessage));
        if (convertReceived(receivedMessage) == checkControllerMessage) { //controller is connected
            System.out.println("Checked successfully");
        }
//        if (errors.contains(convertReceived(receivedMessage))) { //errors handler
//            errorId = executionErrorIds.get(checkControllerMessages.indexOf(checkControllerMessage));
//            return;
//        }

        else if (convertReceived(receivedMessage) == 19 && state != State.WAITING) { //counter at the start
            if (this.state == State.COMING && !isFirst) {
                trainCounter++;
                wagonModel newWagon = new wagonModel(trainCounter, cities.get(0), 0);
                wagonList.add(newWagon);
            } else if (this.state == State.SORTING) {
                trainCounter--;
                wagonList.remove(trainCounter);
                if (trainCounter == 0) {
                    state = State.SORTED;
                }
            }
            isFirst = false;
        }

        else if (convertReceived(receivedMessage) == 21) {
            if(this.state == State.COMING) {
                this.state = State.READY;
            }
        }

        //reaction on messages
        else if (!receivedMessage.get(0) && receivedMessage.get(2)) {
            if (this.state == State.SORTING && convertReceived(receivedMessage) == 63 + 2 * currentWay) {
                counters.set(currentWay - 1, counters.get(currentWay - 1) + 1); // counters at the ends
            } else if (receivedMessage.get(1)) {
                switch (convertReceived(receivedMessage)) {
                    case 99 -> {
                        if ((state == State.READY || state == State.SORTING) && control == Control.FIELD) {
                            sendMessage(35); //semaphore way 1
                            sendMessage(3); //rails way 1
                            currentWay = 1;
                            state = State.SORTING;
                        } else if (control == Control.SERVER) {
                            control = Control.FIELD;
                        }
                    }
                    case 101 -> {
                        if ((state == State.READY || state == State.SORTING) && control == Control.FIELD) {
                            sendMessage(37); //semaphore way 2
                            sendMessage(5); //rails way 2
                            currentWay = 2;
                            state = State.SORTING;
                        } else if (control == Control.SERVER) {
                            control = Control.FIELD;
                        }
                    }
                    case 103 -> {
                        if ((state == State.READY || state == State.SORTING) && control == Control.FIELD) {
                            sendMessage(39); //semaphore way 3
                            sendMessage(7); //rails way 3
                            currentWay = 3;
                            state = State.SORTING;
                        } else if (control == Control.SERVER) {
                            control = Control.FIELD;
                        }
                    }
                    case 105 -> {
                        if ((state == State.READY || state == State.SORTING) && control == Control.FIELD) {
                            sendMessage(41); //semaphore way 4
                            sendMessage(9); //rails way 4
                            currentWay = 4;
                            state = State.SORTING;
                        } else if (control == Control.SERVER) {
                            control = Control.FIELD;
                        }
                    }
                    case 107 -> {
                        if ((state == State.READY || state == State.SORTING) && control == Control.FIELD) {
                            sendMessage(43); //semaphore way 5
                            sendMessage(11); //rails way 5
                            currentWay = 5;
                            state = State.SORTING;
                        } else if (control == Control.SERVER) {
                            control = Control.FIELD;
                        }
                    }
                    case 109 -> {
                        if ((state == State.READY || state == State.SORTING) && control == Control.FIELD) {
                            sendMessage(45); //semaphore way 6
                            sendMessage(13); //rails way 6
                            currentWay = 6;
                            state = State.SORTING;
                        } else if (control == Control.SERVER) {
                            control = Control.FIELD;
                        }
                    }
                    case 111 -> {
                        if(state == State.WAITING && control == Control.FIELD) {
                            sendMessage(15);
                            state = State.COMING;
                        } else if (control == Control.SERVER) {
                            control = Control.FIELD;
                        }
                    }
                    case 113 -> {
                        if(state == State.SORTED && control == Control.FIELD) {
                            sendMessage(49); //semaphore to depot
                            sendMessage(17); //rails to depot
                            currentWay = 8;
                            state = State.LEAVING;
                        } else if (control == Control.SERVER) {
                            control = Control.FIELD;
                        }
                    }
                    case 115 -> {
                        if(state != State.EMERGENCY) {
                            stateBeforeEmergency = state;
                            state = State.EMERGENCY;
                            control = Control.FIELD;
                        } else {
                            state = stateBeforeEmergency;
                        }
                    }
                }
            }
        }
    }


    Runnable listener = () -> {
        //first bad message
        try {
            isFalseMessage = true;
            sendMessage(255);
            isFalseMessage = false;
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (true) {
            try {
                if(state == State.COMING) {
                    checkControllerMessage = checkControllerMessages.get(0);
                    isBusy = true;
                    sendMessage(checkControllerMessage);
                    isBusy = false;
//                    Thread.sleep(500);
                    long delay = System.currentTimeMillis();
                    while (true) {
                        if (System.currentTimeMillis()-delay >= 50) {
                            break;
                        }
                    }
                    while (isTryingToLoadPage) {
                        Thread.onSpinWait();
                    }
                } else {
                    for (int i = 0; i != 4; i++) { /////////////
                        System.out.println("Checking " + i);
                        checkControllerMessage = checkControllerMessages.get(i);
                        isBusy = true;
                        sendMessage(checkControllerMessage);
                        isBusy = false;
                        long delay = System.currentTimeMillis();
                        while (true) {
                            if (System.currentTimeMillis() - delay >= 50) {
                                break;
                            }
                        }
                    }
                    while (isTryingToLoadPage) {
                        Thread.onSpinWait();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };
    Thread threadListener = new Thread(listener);

    long listenerId = threadListener.getId();

    public StationModel(State state, Control control, String name) {
        this.state = state;
        this.control = control;
        this.nameOfStation = name;
        threadListener.setPriority(Thread.MAX_PRIORITY);
        threadListener.start();
    }
}