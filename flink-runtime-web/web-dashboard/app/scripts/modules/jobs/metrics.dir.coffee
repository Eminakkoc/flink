#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

angular.module('flinkApp')

# ----------------------------------------------

.directive 'metricsGraph', ->
  template: '<div class="panel panel-default panel-metric">
               <div class="panel-heading">
                 {{metric.id}}
                 <div class="buttons">
                   <div class="btn-group">
                     <button type="button" ng-class="[btnClasses, {active: metric.size != \'big\'}]" ng-click="setSize(\'small\')">Small</button>
                     <button type="button" ng-class="[btnClasses, {active: metric.size == \'big\'}]" ng-click="setSize(\'big\')">Big</button>
                   </div>
                   <a title="Remove" class="btn btn-default btn-xs remove" ng-click="removeMetric()"><i class="fa fa-close" /></a>
                 </div>
               </div>
               <div class="panel-body">
                  <svg />
               </div>
             </div>'
  replace: true
  scope:
    metric: "="
    window: "="
    removeMetric: "&"
    setMetricSize: "="
    getValues: "&"

  link: (scope, element, attrs) ->
    scope.btnClasses = ['btn', 'btn-default', 'btn-xs']

    scope.value = null
    scope.data = [{
      values: scope.getValues()
    }]

    scope.options = {
      x: (d, i) ->
        d.x
      y: (d, i) ->
        d.y

      xTickFormat: (d) ->
        d3.time.format('%H:%M:%S')(new Date(d))

      yTickFormat: (d) ->
        if d >= 1000000
          "#{d / 1000000}m"
        else if d >= 1000
          "#{d / 1000}k"
        else
          d
    }

    scope.showChart = ->
      d3.select(element.find("svg")[0])
      .datum(scope.data)
      .transition().duration(250)
      .call(scope.chart)

    scope.chart = nv.models.lineChart()
      .options(scope.options)
      .showLegend(false)
      .margin({
        top: 15
        left: 50
        bottom: 30
        right: 30
      })

    scope.chart.yAxis.showMaxMin(false)
    scope.chart.tooltip.hideDelay(0)
    scope.chart.tooltip.contentGenerator((obj) ->
      "<p>#{d3.time.format('%H:%M:%S')(new Date(obj.point.x))} | #{obj.point.y}</p>"
    )

    nv.utils.windowResize(scope.chart.update);

#    scope.remove = ->
#      scope.$destroy()

    scope.setSize = (size) ->
      scope.setMetricSize(scope.metric, size)
#      scope.metric.size = size
#      scope.chart.update()

    scope.showChart()

    scope.$on 'metrics:data:update', (event, timestamp, data) ->
#      console.log data, scope.metric, scope.metric.id
      scope.value = parseInt(data[scope.metric.id])

      scope.data[0].values.push {
        x: timestamp
        y: scope.value
      }

      if scope.data[0].values.length > scope.window
        scope.data[0].values.shift()

      scope.showChart()
      scope.chart.clearHighlights()
      scope.chart.tooltip.hidden(true)
