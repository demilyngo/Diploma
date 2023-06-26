document.querySelector(".modal-click").disabled = true;

//ORDER OF WAGONS
function checkOrder() {
    var str = $( "form" ).serializeArray();
    var order = "";
    var res = "order=";
    str.forEach(function(elem) {
        order += elem.value;
    });
    res += order.split('').reverse().join('');
    return res
}
$( "select" ).on( "change", checkOrder );



//SEND TO STARTING POSITION
let cities = ["Москва", "Казань", "Магадан", "Воркута", "Якутск", "Тюмень"];
var toSortCounter = parseInt($("#toSortCounter").text(), 10);
$("#waitButton").click(function (e) {
    e.preventDefault();
    $(".state").text("Состояние: Прибытие");
    document.querySelector("#waitButton").style.display = "none";
    document.querySelector("#overlay").style.display = "block";

    if (window.EventSource == null) {
        alert('The browser does not support Server-Sent Events');
    } else {
        var eventSource = new EventSource('/wait');
        eventSource.onopen = function () {
            console.log('connection is established');
        };
        eventSource.onerror = function (error) {
            console.log('connection state: ' + eventSource.readyState + ', error: ' + error);
        };
        eventSource.onmessage = function (event) {
            console.log('id: ' + event.lastEventId + ', data: ' + event.data);
            switch (event.lastEventId ) {
                case "1":
                    if(toSortCounter > 0) {
                        let template = " " +
                            "                    <div class=\"wagonItem\" id=\"wagonItem" + toSortCounter + "\">\n" +
                            "                        <div class=\"wagonNumber\">\n" +
                            "                            <p>" + toSortCounter + "</p>\n" +
                            "                        </div>\n" +
                            "                        <select class=\"selectWagon\" name=\"wagon\">\n" +
                            "                            <option value=\"" + cities.indexOf(event.data) + "\">" + event.data + "</option>\n"
                        cities.forEach(element => {
                            if (element !== event.data)
                                template += "                            <option value=\"" + cities.indexOf(element) + "\">" + element + "</option>\n";
                        })
                        template += "                        </select>\n" +
                            "\n" +
                            "                        <input type=\"checkbox\" class=\"type\"/>\n" +
                            "                    </div>";
                        document.querySelector(".wagonItems").innerHTML += template;
                        $("#toSortCounter").text(toSortCounter);
                    }
                    toSortCounter+=1;
                    break;

                case "2":
                    $(".state").text("Состояние: Готово к сортировке");
                    document.querySelector("#startButton").style.display = "block";
                    document.querySelector("#overlay").style.display = "none"
                    eventSource.close();
                    break;

                case "3":
                    if(event.data < 5) {
                        $("#controllerError").text("Ошибка в работе контроллера " + event.data);
                    }
                    else {
                        $("#controllerError").text("Ошибка в подключении контроллера " + event.data-4);
                    }

                    $(".modal-click").modal({fadeDuration: 250});
                    eventSource.close();
                    document.querySelector("#restartButton").style.display = "block";
                    break;
            }
        };
    }
})

//START SORT
$("#startButton").click(function(e) {
    document.querySelector("#startButton").style.display = "none";
    document.querySelector("#overlay").style.display = "block";
    e.preventDefault();
    order = checkOrder();
    toSortCounter--;

    $(".state").text("Состояние: Сортировка");
    var wagonList = document.querySelectorAll(".selectWagon");
    for (let el of wagonList) {el.disabled = true}


    if (window.EventSource == null) {
        alert('The browser does not support Server-Sent Events');
    } else {
        console.log(order);
        var eventSource = new EventSource('/start?' + order);
        eventSource.onopen = function () {
            console.log('connection is established');
        };
        eventSource.onerror = function (error) {
            console.log('connection state: ' + eventSource.readyState + ', error: ' + error);
        };
        eventSource.onmessage = function (event) {
            console.log('id: ' + event.lastEventId + ', data: ' + event.data);
            switch (event.lastEventId) {
                case "1":
                    document.querySelector(".wagonItems").removeChild(document.querySelector("#wagonItem" + toSortCounter));
                    toSortCounter -= 1;
                    $("#toSortCounter").text(toSortCounter);
                    $(".map").attr("src", "../images/Map_" + event.data + ".png");
                    break;
                case "2":
                    var cityToAddCounter = $("#" + cities[parseInt(event.data, 10) - 1]);
                    cityToAddCounter.text(parseInt(cityToAddCounter.text(), 10) + 1);
                    break;
                case "3":
                    $(".state").text("Состояние: Отсортировано");
                    document.querySelector("#restartButton").style.display = "block";
                    $(".map").attr("src", "../images/Map_8.png");
                    eventSource.close();
                    console.log('connection is closed');
                    break;
                case "4":
                    if(event.data < 5) {
                        $("#controllerError").text("Ошибка в работе контроллера " + event.data);
                    }
                    else {
                        $("#controllerError").text("Ошибка в подключении контроллера " + event.data-4);
                    }

                    $(".modal-click").modal({fadeDuration: 250});
                    eventSource.close();
                    document.querySelector("#restartButton").style.display = "block";
                    break;
            }
        };
    }
})

$(document).ready(function () {
    if (window.EventSource == null) {
        alert('The browser does not support Server-Sent Events');
    } else {
        var eventSourceField = new EventSource('/field');
        eventSourceField.onopen = function () {
            console.log('connection is established FIELD');
        };
        eventSourceField.onerror = function (error) {
            console.log('connection state: ' + eventSourceField.readyState + ', error: ' + error);
        };
        eventSourceField.onmessage = function (eventField) {
            console.log('id: ' + eventField.lastEventId + ', data: ' + eventField.data);
            switch (eventField.lastEventId) {
                case "5":
                    $("#control").text("Управление по месту");
                    var prevState = $(".state").text();
                    $(".state").text("Состояние: Авария");
                    $("#controllerError").text("Авария. Ожидайте устранения неполадок.");
                    document.querySelector(".bottomRight").style.display = "none"
                    $(".modal-click").modal({fadeDuration: 250});
                    break;
                case "6":
                    document.querySelector(".bottomRight").style.display = "block";
                    $(".state").text(prevState);
                    break;
                case "7":
                    $("#control").text("Управление по месту");
                    document.querySelector(".mainButtons").style.display = "none"
                    document.querySelector("#takeControlButton").style.display = "block"
                    document.querySelector("#overlay").style.display = "block"
                    break;
                case "8":
                    console.log(toSortCounter);
                    toSortCounter -= 1;
                    document.querySelector(".wagonItems").removeChild(document.querySelector("#wagonItem" + toSortCounter));
                    $("#toSortCounter").text(toSortCounter);
                    $(".map").attr("src", "../images/Map_" + eventField.data + ".png");
                    break;
                case "9":
                    var cityToAddCounter = $("#" + cities[parseInt(eventField.data, 10) - 1]);
                    cityToAddCounter.text(parseInt(cityToAddCounter.text(), 10) + 1);
                    break;
            }
        };
    }
});

// $("#restartButton").click(function(e) {
//     document.querySelector(".wagonItems").innerHTML = "";
//     $("#toSortCounter").text(0);
//     cities.forEach(element => {
//         $("#" + element).text(0);
//     })
// })
//
// $("#alarmButton").click(function(e) {
//     $(".modal-click").modal({fadeDuration: 250});
//     document.querySelector("#waitButton").style.display = "none";
//     document.querySelector("#startButton").style.display = "none";
//     document.querySelector("#restartButton").style.display = "block";
// })