/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
var showControllersOnly = false;
var seriesFilter = "";
var filtersOnlySampleSeries = true;

/*
 * Add header in statistics table to group metrics by category
 * format
 *
 */
function summaryTableHeader(header) {
    var newRow = header.insertRow(-1);
    newRow.className = "tablesorter-no-sort";
    var cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Requests";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 3;
    cell.innerHTML = "Executions";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 7;
    cell.innerHTML = "Response Times (ms)";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Throughput";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 2;
    cell.innerHTML = "Network (KB/sec)";
    newRow.appendChild(cell);
}

/*
 * Populates the table identified by id parameter with the specified data and
 * format
 *
 */
function createTable(table, info, formatter, defaultSorts, seriesIndex, headerCreator) {
    var tableRef = table[0];

    // Create header and populate it with data.titles array
    var header = tableRef.createTHead();

    // Call callback is available
    if(headerCreator) {
        headerCreator(header);
    }

    var newRow = header.insertRow(-1);
    for (var index = 0; index < info.titles.length; index++) {
        var cell = document.createElement('th');
        cell.innerHTML = info.titles[index];
        newRow.appendChild(cell);
    }

    var tBody;

    // Create overall body if defined
    if(info.overall){
        tBody = document.createElement('tbody');
        tBody.className = "tablesorter-no-sort";
        tableRef.appendChild(tBody);
        var newRow = tBody.insertRow(-1);
        var data = info.overall.data;
        for(var index=0;index < data.length; index++){
            var cell = newRow.insertCell(-1);
            cell.innerHTML = formatter ? formatter(index, data[index]): data[index];
        }
    }

    // Create regular body
    tBody = document.createElement('tbody');
    tableRef.appendChild(tBody);

    var regexp;
    if(seriesFilter) {
        regexp = new RegExp(seriesFilter, 'i');
    }
    // Populate body with data.items array
    for(var index=0; index < info.items.length; index++){
        var item = info.items[index];
        if((!regexp || filtersOnlySampleSeries && !info.supportsControllersDiscrimination || regexp.test(item.data[seriesIndex]))
                &&
                (!showControllersOnly || !info.supportsControllersDiscrimination || item.isController)){
            if(item.data.length > 0) {
                var newRow = tBody.insertRow(-1);
                for(var col=0; col < item.data.length; col++){
                    var cell = newRow.insertCell(-1);
                    cell.innerHTML = formatter ? formatter(col, item.data[col]) : item.data[col];
                }
            }
        }
    }

    // Add support of columns sort
    table.tablesorter({sortList : defaultSorts});
}

$(document).ready(function() {

    // Customize table sorter default options
    $.extend( $.tablesorter.defaults, {
        theme: 'blue',
        cssInfoBlock: "tablesorter-no-sort",
        widthFixed: true,
        widgets: ['zebra']
    });

    var data = {"OkPercent": 97.19101123595506, "KoPercent": 2.808988764044944};
    var dataset = [
        {
            "label" : "FAIL",
            "data" : data.KoPercent,
            "color" : "#FF6347"
        },
        {
            "label" : "PASS",
            "data" : data.OkPercent,
            "color" : "#9ACD32"
        }];
    $.plot($("#flot-requests-summary"), dataset, {
        series : {
            pie : {
                show : true,
                radius : 1,
                label : {
                    show : true,
                    radius : 3 / 4,
                    formatter : function(label, series) {
                        return '<div style="font-size:8pt;text-align:center;padding:2px;color:white;">'
                            + label
                            + '<br/>'
                            + Math.round10(series.percent, -2)
                            + '%</div>';
                    },
                    background : {
                        opacity : 0.5,
                        color : '#000'
                    }
                }
            }
        },
        legend : {
            show : true
        }
    });

    // Creates APDEX table
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.8216292134831461, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [0.9242424242424242, 500, 1500, "GET /api/v1/notifications/my-notifications"], "isController": false}, {"data": [0.8365384615384616, 500, 1500, "GET /api/v1/ledger/accounts/1/balance"], "isController": false}, {"data": [0.7142857142857143, 500, 1500, "POST /api/v1/ledger/transfers (Ping: 1 -> 2)"], "isController": false}, {"data": [1.0, 500, 1500, "AUTH: Login Counterparty Customer (maria_santos)"], "isController": false}, {"data": [0.7, 500, 1500, "AUTH: Login Primary Customer (john_doe)"], "isController": false}, {"data": [0.8262411347517731, 500, 1500, "GET /api/v1/accounts/my-accounts"], "isController": false}, {"data": [0.7575757575757576, 500, 1500, "POST /api/v1/ledger/transfers (Pong: 2 -> 1)"], "isController": false}]}, function(index, item){
        switch(index){
            case 0:
                item = item.toFixed(3);
                break;
            case 1:
            case 2:
                item = formatDuration(item);
                break;
        }
        return item;
    }, [[0, 0]], 3);

    // Create statistics table
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 356, 10, 2.808988764044944, 488.27808988764014, 13, 6522, 109.5, 1451.9, 2083.3, 5143.43, 5.819465786118285, 7.251919218131886, 3.482094874865139], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["GET /api/v1/notifications/my-notifications", 33, 0, 0.0, 276.48484848484844, 20, 1448, 111.0, 1073.0000000000011, 1426.3, 1448.0, 0.667300264897984, 3.4880617391765916, 0.38122134273957087], "isController": false}, {"data": ["GET /api/v1/ledger/accounts/1/balance", 104, 3, 2.8846153846153846, 447.67307692307696, 13, 5144, 59.0, 1522.5, 2164.5, 5143.95, 1.909237773535027, 1.4078513100767367, 1.081404207666324], "isController": false}, {"data": ["POST /api/v1/ledger/transfers (Ping: 1 -> 2)", 35, 1, 2.857142857142857, 727.5714285714284, 48, 3791, 381.0, 1832.3999999999999, 2305.399999999992, 3791.0, 0.6385812549033917, 0.5855740218303563, 0.49951521990001646], "isController": false}, {"data": ["AUTH: Login Counterparty Customer (maria_santos)", 5, 0, 0.0, 112.2, 94, 145, 102.0, 145.0, 145.0, 145.0, 0.8871540099361248, 1.0831249445528743, 0.24084845191625265], "isController": false}, {"data": ["AUTH: Login Primary Customer (john_doe)", 5, 0, 0.0, 701.0, 119, 2504, 144.0, 2504.0, 2504.0, 2504.0, 0.6230529595015577, 0.7545998831775701, 0.16671534267912771], "isController": false}, {"data": ["GET /api/v1/accounts/my-accounts", 141, 4, 2.8368794326241136, 452.9503546099289, 22, 5429, 107.0, 1307.0, 1837.8, 4793.120000000019, 2.4348546858001345, 2.071002564368233, 1.3672279729834742], "isController": false}, {"data": ["POST /api/v1/ledger/transfers (Pong: 2 -> 1)", 33, 2, 6.0606060606060606, 749.9393939393939, 39, 6522, 286.0, 1851.8000000000009, 4642.499999999993, 6522.0, 0.6727005870841487, 0.6168611828828279, 0.5301458728290118], "isController": false}]}, function(index, item){
        switch(index){
            // Errors pct
            case 3:
                item = item.toFixed(2) + '%';
                break;
            // Mean
            case 4:
            // Mean
            case 7:
            // Median
            case 8:
            // Percentile 1
            case 9:
            // Percentile 2
            case 10:
            // Percentile 3
            case 11:
            // Throughput
            case 12:
            // Kbytes/s
            case 13:
            // Sent Kbytes/s
                item = item.toFixed(2);
                break;
        }
        return item;
    }, [[0, 0]], 0, summaryTableHeader);

    // Create error table
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": [{"data": ["The operation lasted too long: It took 3,791 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 2, 20.0, 0.5617977528089888], "isController": false}, {"data": ["The operation lasted too long: It took 3,915 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, 10.0, 0.2808988764044944], "isController": false}, {"data": ["The operation lasted too long: It took 5,429 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, 10.0, 0.2808988764044944], "isController": false}, {"data": ["The operation lasted too long: It took 3,405 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, 10.0, 0.2808988764044944], "isController": false}, {"data": ["The operation lasted too long: It took 5,098 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, 10.0, 0.2808988764044944], "isController": false}, {"data": ["The operation lasted too long: It took 5,144 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, 10.0, 0.2808988764044944], "isController": false}, {"data": ["The operation lasted too long: It took 5,143 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, 10.0, 0.2808988764044944], "isController": false}, {"data": ["The operation lasted too long: It took 3,837 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, 10.0, 0.2808988764044944], "isController": false}, {"data": ["The operation lasted too long: It took 6,522 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, 10.0, 0.2808988764044944], "isController": false}]}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 356, 10, "The operation lasted too long: It took 3,791 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 2, "The operation lasted too long: It took 3,915 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "The operation lasted too long: It took 5,429 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "The operation lasted too long: It took 3,405 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "The operation lasted too long: It took 5,098 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": ["GET /api/v1/ledger/accounts/1/balance", 104, 3, "The operation lasted too long: It took 5,098 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "The operation lasted too long: It took 5,144 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "The operation lasted too long: It took 5,143 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "", "", "", ""], "isController": false}, {"data": ["POST /api/v1/ledger/transfers (Ping: 1 -> 2)", 35, 1, "The operation lasted too long: It took 3,791 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "", "", "", "", "", "", "", ""], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": ["GET /api/v1/accounts/my-accounts", 141, 4, "The operation lasted too long: It took 3,791 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "The operation lasted too long: It took 3,915 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "The operation lasted too long: It took 5,429 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "The operation lasted too long: It took 3,405 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "", ""], "isController": false}, {"data": ["POST /api/v1/ledger/transfers (Pong: 2 -> 1)", 33, 2, "The operation lasted too long: It took 3,837 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "The operation lasted too long: It took 6,522 milliseconds, but should not have lasted longer than 3,000 milliseconds.", 1, "", "", "", "", "", ""], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
