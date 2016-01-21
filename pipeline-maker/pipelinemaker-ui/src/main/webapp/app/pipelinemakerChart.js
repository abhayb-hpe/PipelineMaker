/*
 (c) Copyright 2012,2013 Hewlett-Packard Development Company, L.P.

 HP SKI Framework Reference Implementation

 @author Shaila Shree
 @author Frank Wood
 */

// JSLint directive...
/*global $: false, SKI: false */

(function (api) {
    'use strict';

    // framework APIs
    var f = api.fn,
        def = api.def,
        v = api.view;

    // snap-in libs
    var t = api.lib.htmlTags;

    // common code
    var chart1,
        chart2;

    f.trace('including pipelinemakerChart.js');

    function mkChart1(containerId) {
        return new Highcharts.Chart({
            chart: {
                renderTo: containerId
            },
            title: {
                text: 'PipelineMaker Data'
            },
            xAxis: {
                ordinal: true,
                type: 'datetime'
            },

            series: [{
                data: [
                    [Date.UTC(2011, 0, 1), 3],
                    [Date.UTC(2011, 0, 2), 5],
                    [Date.UTC(2011, 0, 3), 1],
                    [Date.UTC(2011, 0, 7), 3],
                    [Date.UTC(2011, 0, 8), 1],
                    [Date.UTC(2011, 0, 9), 4]

                ]
            }, {
                data: [
                    [Date.UTC(2011, 0, 1), 2],
                    [Date.UTC(2011, 0, 2), 1],
                    [Date.UTC(2011, 0, 3), 5],
                    [Date.UTC(2011, 0, 7), 7],
                    [Date.UTC(2011, 0, 8), 3],
                    [Date.UTC(2011, 0, 9), 5]
                ]
            }]
        });
    }

    function mkChart2(containerId) {
        return new Highcharts.Chart({
            chart: {
                renderTo: containerId
            },
            title: {
                text: 'PipelineMaker categories'
            },
            tooltip: {
                pointFormat: '{series.name}: <b>{point.percentage}%</b>',
                percentageDecimals: 1
            },
            plotOptions: {
                pie: {
                    dataLabels: {
                        enabled: true,
                        formatter: function() {
                            return this.point.name + ' ' +
                                   this.percentage +' %';
                        }
                    }
                }
            },
            series: [{
                type: 'pie',
                name: 'PipelineMaker share',
                data: [
                    ['Firefox',   45.0],
                    ['IE',       26.8],
                    {
                        name: 'Chrome',
                        y: 12.8,
                        sliced: true,
                        selected: true
                    },
                    ['Safari',    8.5],
                    ['Opera',     6.2],
                    ['Others',   0.7]
                ]
            }]
        });
    }

    function create(view) {
        // create 2 containers for the charts that take up 1/3 of the view
        var container1 = t.div({
                id: 'container1',
                css: {
                    height: '33%',
                    'min-height': '200px',
                    'min-width': '400px'
                }
            }),
            container2 = t.div({
                id: 'container2',
                css: {
                    'margin-top': '10px',
                    height: '33%',
                    width: '60%',
                    'min-height': '300px',
                    'min-width': '400px'
                }
            }),
            link = t.a({
                css: {
                    display: 'inline-block',
                    margin: '8px'
                },
                attr: {
                    href: 'http://www.highcharts.com/'
                }
            }).append('Highcharts');

        return t.div({
                css: {
                    height: '100%'
                }
            }).append(
            container1,
            container2,
            t.p().append(link)
        );
    }

    /* Note that the charts are being destroyed and created each 'load' and
     * 'resize'.  I could NOT find way to get the charts to simply resize
     * without errors. I'm leaving this as an exercise for later.
     */
    function createCharts() {
        if (chart1) {
            chart1.destroy();
        }
        chart1 = mkChart1('container1');

        if (chart2) {
            chart2.destroy();
        }
        chart2 = mkChart2('container2');
    }

    function preload(view) {
        v.fullHeight();
    }

    function load(view) {
        createCharts();
    }

    function resize(view) {
        createCharts();
    }

    def.addView('pipelinemakerTask', {
        create: create,
        preload: preload,
        load: load,
        resize: resize
    });

}(SKI));
