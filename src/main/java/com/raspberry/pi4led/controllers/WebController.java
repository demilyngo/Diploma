package com.raspberry.pi4led.controllers;

import com.raspberry.pi4led.models.Control;
import com.raspberry.pi4led.models.State;
import com.raspberry.pi4led.models.StationModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller
public class WebController {
    private final ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    final StationModel stationModel = new StationModel(State.WAITING, Control.SERVER, "Сургутская");


    @GetMapping("/")
    public String greeting(Model model) throws InterruptedException {
        stationModel.setTryingToLoadPage(true);
        while(stationModel.isBusy()) {
            Thread.onSpinWait();
        }

        model.addAttribute("station", stationModel);
        model.addAttribute("cities", stationModel.getCities());
        model.addAttribute("counters", stationModel.getCounters());
        model.addAttribute("wagonList", stationModel.getWagonList());
        stationModel.setTryingToLoadPage(false);
        stationModel.sendMessage(49);
        stationModel.sendMessage(17);
        return "index";
    }

    @ResponseBody
    @GetMapping(path = "/wait", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter prepareForSorting()  {
        SseEmitter emitter = new SseEmitter(-1L);
        cachedThreadPool.execute(() -> {
            if(stationModel.getState() == State.WAITING) {
                stationModel.setState(State.COMING);
                //cached thread pool
                try {
                    stationModel.sendMessage(15); //moving to position for sorting
                    while (stationModel.convertReceived(stationModel.getReceivedMessage()) != 21) {
                        if (stationModel.convertReceived(stationModel.getReceivedMessage()) == 19) {
                            var eventBuilder = SseEmitter.event();
                            eventBuilder.id("1").data(stationModel.getCities().get(0));
                            emitter.send(eventBuilder);
                            stationModel.getReceivedMessage().clear();
                            continue;
                        }

                    }
                    var eventBuilder = SseEmitter.event();
                    stationModel.setState(State.READY);
                    eventBuilder.id("2").data("Ready to sort").build();
                    emitter.send(eventBuilder);

                    if (stationModel.getErrorId() != 0) {
                        eventBuilder = SseEmitter.event();
                        eventBuilder.id("3").data(stationModel.getErrorId()); //to open modal with error
                        emitter.send(eventBuilder);
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        return emitter;
    }

    @GetMapping(path = "/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter startSorting(@RequestParam(value = "order", defaultValue = "0") String order) {
        System.out.println(stationModel.getTrainCounter());
        SseEmitter emitter = new SseEmitter(-1L);
        cachedThreadPool.execute(() -> {
            stationModel.setState(State.SORTING);
            try {
                for(char way : order.toCharArray()) {
                    stationModel.setCurrentWay(Character.getNumericValue(way)+1);
                    var eventBuilder = SseEmitter.event();

                    int msgToSemaphore = 33 + (2 * stationModel.getCurrentWay());
                    int msgToArrows = 1 + (2 * stationModel.getCurrentWay());
                    int msgToReceive = 65+(2 * stationModel.getCurrentWay());

                    if (stationModel.getErrorId() != 0) {
                        eventBuilder.id("4").data(stationModel.getErrorId()); //to open modal with error
                        emitter.send(eventBuilder);
                        break;
                    }

                    stationModel.sendMessage(msgToSemaphore); //message to change semaphores
                    stationModel.sendMessage(msgToArrows); //message to change arrows

                    eventBuilder.id("1").data(stationModel.getCurrentWay()).build();
                    emitter.send(eventBuilder);
                    while(stationModel.convertReceived(stationModel.getReceivedMessage())!=msgToReceive) {
                        Thread.onSpinWait();
                    }
                    eventBuilder = SseEmitter.event();
                    eventBuilder.id("2").data(stationModel.getCurrentWay()).build();
                    emitter.send(eventBuilder);
                }
                stationModel.sendMessage(49);
                stationModel.sendMessage(17);
                var eventBuilder = SseEmitter.event();
                stationModel.setState(State.SORTED);
                eventBuilder.id("3").data("Done sorting").build();
                emitter.send(eventBuilder);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @ResponseBody
    @GetMapping(path = "/field", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter emergency() {
        SseEmitter emitter = new SseEmitter(-1L);
        cachedThreadPool.execute(() -> {
            while(true) {
                try {
                    if (stationModel.convertReceived(stationModel.getReceivedMessage()) == 115) {
                        var eventBuilder = SseEmitter.event();
                        eventBuilder.id(stationModel.getState() == State.EMERGENCY ? "6" : "5").data("Emergency toggle").build();
                        try {
                            emitter.send(eventBuilder);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        while(stationModel.getReceivedMessage().nextSetBit(0)!=-1) {
                            Thread.onSpinWait();
                        }
                    } else if (stationModel.convertReceived(stationModel.getReceivedMessage()) > 97 && stationModel.convertReceived(stationModel.getReceivedMessage()) < 115 && stationModel.getControl() == Control.SERVER) {
                        var eventBuilder = SseEmitter.event();
                        eventBuilder.id("7").data("Field control").build();
                        emitter.send(eventBuilder);
                        while(stationModel.getReceivedMessage().nextSetBit(0)!=-1) {
                            Thread.onSpinWait();
                        }
                    } else if (stationModel.convertReceived(stationModel.getReceivedMessage()) > 97 && stationModel.convertReceived(stationModel.getReceivedMessage()) < 115 && stationModel.getControl() == Control.FIELD) {
                        var eventBuilder = SseEmitter.event();
                        eventBuilder.id("8").data(stationModel.getCurrentWay()).build();
                        emitter.send(eventBuilder);
//                        stationModel.setWagonSorting(true);
                        while (stationModel.convertReceived(stationModel.getReceivedMessage()) != 65 + 2 * stationModel.getCurrentWay()) {
                            Thread.onSpinWait();
                        }
//                        stationModel.setWagonSorting(false);
                        eventBuilder = SseEmitter.event();
                        eventBuilder.id("9").data(stationModel.getCurrentWay()).build();
                        emitter.send(eventBuilder);
                        while(stationModel.getReceivedMessage().nextSetBit(0)!=-1) {
                            Thread.onSpinWait();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        return emitter;
    }

    @GetMapping("/takeControl")
    public void takeControl() {
        stationModel.setControl(Control.SERVER);
    }

    @GetMapping("/restart")
    public String restartSystem() {
        stationModel.setErrorId(0);
        stationModel.setFirst(true);
        stationModel.setState(State.WAITING);
        stationModel.setTrainCounter(0);
        stationModel.getWagonList().clear();
        for (int i =0; i!= stationModel.getCounters().size(); i++) {
            stationModel.getCounters().set(i, 0);
        }
        stationModel.setCurrentWay(8);
        if(!stationModel.getThreadListener().isAlive()) {
            stationModel.getThreadListener().start();
        }
        return "redirect:/";
    }
}