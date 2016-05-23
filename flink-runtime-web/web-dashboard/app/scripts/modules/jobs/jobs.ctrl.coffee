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

.controller 'RunningJobsController', ($scope, $state, $stateParams, JobsService) ->
  $scope.jobObserver = ->
    $scope.jobs = JobsService.getJobs('running')

  JobsService.registerObserver($scope.jobObserver)
  $scope.$on '$destroy', ->
    JobsService.unRegisterObserver($scope.jobObserver)

  $scope.jobObserver()

# --------------------------------------

.controller 'CompletedJobsController', ($scope, $state, $stateParams, JobsService) ->
  $scope.jobObserver = ->
    $scope.jobs = JobsService.getJobs('finished')

  JobsService.registerObserver($scope.jobObserver)
  $scope.$on '$destroy', ->
    JobsService.unRegisterObserver($scope.jobObserver)

  $scope.jobObserver()

# --------------------------------------

.controller 'SingleJobController', ($scope, $state, $stateParams, JobsService, $rootScope, flinkConfig, $interval) ->
  console.log 'SingleJobController'

  $scope.jobid = $stateParams.jobid
  $scope.job = null
  $scope.plan = null
  $scope.vertices = null
  $scope.jobCheckpointStats = null
  $scope.showHistory = false
  $scope.backPressureOperatorStats = {}

  JobsService.loadJob($stateParams.jobid).then (data) ->
    $scope.job = data
    $scope.plan = data.plan
    $scope.vertices = data.vertices

  refresher = $interval ->
    JobsService.loadJob($stateParams.jobid).then (data) ->
      $scope.job = data

      $scope.$broadcast 'reload'

  , flinkConfig["refresh-interval"]

  $scope.$on '$destroy', ->
    $scope.job = null
    $scope.plan = null
    $scope.vertices = null
    $scope.jobCheckpointStats = null
    $scope.backPressureOperatorStats = null

    $interval.cancel(refresher)

  $scope.cancelJob = (cancelEvent) ->
    angular.element(cancelEvent.currentTarget).removeClass("btn").removeClass("btn-default").html('Cancelling...')
    JobsService.cancelJob($stateParams.jobid).then (data) ->
      {}

  $scope.stopJob = (stopEvent) ->
    angular.element(stopEvent.currentTarget).removeClass("btn").removeClass("btn-default").html('Stopping...')
    JobsService.stopJob($stateParams.jobid).then (data) ->
      {}

  $scope.toggleHistory = ->
    $scope.showHistory = !$scope.showHistory

# --------------------------------------

.controller 'JobPlanController', ($scope, $state, $stateParams, JobsService) ->
  console.log 'JobPlanController'

  $scope.nodeid = null
  $scope.nodeUnfolded = false
  $scope.stateList = JobsService.stateList()

  $scope.changeNode = (nodeid) ->
    if nodeid != $scope.nodeid
      $scope.nodeid = nodeid
      $scope.vertex = null
      $scope.subtasks = null
      $scope.accumulators = null
      $scope.operatorCheckpointStats = null

      $scope.$broadcast 'reload'

    else
      $scope.nodeid = null
      $scope.nodeUnfolded = false
      $scope.vertex = null
      $scope.subtasks = null
      $scope.accumulators = null
      $scope.operatorCheckpointStats = null

  $scope.deactivateNode = ->
    $scope.nodeid = null
    $scope.nodeUnfolded = false
    $scope.vertex = null
    $scope.subtasks = null
    $scope.accumulators = null
    $scope.operatorCheckpointStats = null

  $scope.toggleFold = ->
    $scope.nodeUnfolded = !$scope.nodeUnfolded

# --------------------------------------

.controller 'JobPlanSubtasksController', ($scope, JobsService) ->
  console.log 'JobPlanSubtasksController'

  getSubtasks = ->
    JobsService.getSubtasks($scope.nodeid).then (data) ->
      $scope.subtasks = data

  if $scope.nodeid and (!$scope.vertex or !$scope.vertex.st)
    getSubtasks()

  $scope.$on 'reload', (event) ->
    console.log 'JobPlanSubtasksController'
    getSubtasks() if $scope.nodeid

# --------------------------------------

.controller 'JobPlanTaskManagersController', ($scope, JobsService) ->
  console.log 'JobPlanTaskManagersController'

  getTaskManagers = ->
    JobsService.getTaskManagers($scope.nodeid).then (data) ->
      $scope.taskmanagers = data

  if $scope.nodeid and (!$scope.vertex or !$scope.vertex.st)
    getTaskManagers()

  $scope.$on 'reload', (event) ->
    console.log 'JobPlanTaskManagersController'
    getTaskManagers() if $scope.nodeid

# --------------------------------------

.controller 'JobPlanAccumulatorsController', ($scope, JobsService) ->
  console.log 'JobPlanAccumulatorsController'

  getAccumulators = ->
    JobsService.getAccumulators($scope.nodeid).then (data) ->
      $scope.accumulators = data.main
      $scope.subtaskAccumulators = data.subtasks

  if $scope.nodeid and (!$scope.vertex or !$scope.vertex.accumulators)
    getAccumulators()

  $scope.$on 'reload', (event) ->
    console.log 'JobPlanAccumulatorsController'
    getAccumulators() if $scope.nodeid

# --------------------------------------

.controller 'JobPlanCheckpointsController', ($scope, JobsService) ->
  console.log 'JobPlanCheckpointsController'

  getJobCheckpointStats = ->
    JobsService.getJobCheckpointStats($scope.jobid).then (data) ->
      $scope.jobCheckpointStats = data

  getOperatorCheckpointStats = ->
    JobsService.getOperatorCheckpointStats($scope.nodeid).then (data) ->
      $scope.operatorCheckpointStats = data.operatorStats
      $scope.subtasksCheckpointStats = data.subtasksStats

  # Get the per job stats
  getJobCheckpointStats()

  # Get the per operator stats
  if $scope.nodeid and (!$scope.vertex or !$scope.vertex.operatorCheckpointStats)
    getOperatorCheckpointStats()

  $scope.$on 'reload', (event) ->
    console.log 'JobPlanCheckpointsController'

    getJobCheckpointStats()
    getOperatorCheckpointStats() if $scope.nodeid

# --------------------------------------

.controller 'JobPlanBackPressureController', ($scope, JobsService) ->
  console.log 'JobPlanBackPressureController'

  getOperatorBackPressure = ->
    $scope.now = Date.now()

    if $scope.nodeid
      JobsService.getOperatorBackPressure($scope.nodeid).then (data) ->
      $scope.backPressureOperatorStats[$scope.nodeid] = data

  getOperatorBackPressure()

  $scope.$on 'reload', (event) ->
    console.log 'JobPlanBackPressureController (reload)'
    getOperatorBackPressure()

# --------------------------------------

.controller 'JobTimelineVertexController', ($scope, $state, $stateParams, JobsService) ->
  console.log 'JobTimelineVertexController'

  getVertex = ->
    JobsService.getVertex($stateParams.vertexId).then (data) ->
      $scope.vertex = data

  getVertex()

  $scope.$on 'reload', (event) ->
    console.log 'JobTimelineVertexController'
    getVertex()

# --------------------------------------

.controller 'JobExceptionsController', ($scope, $state, $stateParams, JobsService) ->
  JobsService.loadExceptions().then (data) ->
    $scope.exceptions = data

# --------------------------------------

.controller 'JobPropertiesController', ($scope, JobsService) ->
  console.log 'JobPropertiesController'

  $scope.changeNode = (nodeid) ->
    if nodeid != $scope.nodeid
      $scope.nodeid = nodeid

      JobsService.getNode(nodeid).then (data) ->
        $scope.node = data

    else
      $scope.nodeid = null
      $scope.node = null

# --------------------------------------

.controller 'JobPlanMetricsController', ($scope, JobsService, MetricsService) ->
  console.log 'JobPlanMetricsController'

  $scope.dragging = false

  sine = ->
    sin = []
    now = +new Date
    i = 0
    while i < 100
      sin.push
        x: now + i * 1000 * 60 * 60 * 24
        y: Math.sin(i / 10)
      i++
    sin

  loadMetrics = ->
    JobsService.getVertex($scope.nodeid).then (data) ->
      $scope.vertex = data

    MetricsService.getAvailableMetrics($scope.jobid, $scope.nodeid).then (data) ->
      $scope.availableMetrics = data
#      console.log data
#      ids = []
#      angular.forEach data, (v, k) ->
#        ids.push(v.id)

      setup = MetricsService.getMetricsSetup($scope.jobid, $scope.nodeid)
      $scope.metrics = setup.names

      MetricsService.getMetrics($scope.jobid, $scope.nodeid, setup.names).then (data) ->
        $scope.$broadcast "metrics:data:update", data

  $scope.options = chart:
    type: 'sparklinePlus'
    height: 150
    x: (d, i) ->
      i
    xTickFormat: (d) ->
      d3.time.format('%x') new Date($scope.data[d].x)
    duration: 250

  $scope.data = sine();

  $scope.dropped = (event, index, item, external, type) ->
#    console.log event
#    console.log index
#    console.log item
#    console.log external
#    console.log type

    MetricsService.orderMetrics($scope.jobid, $scope.nodeid, item, index)
    loadMetrics()
    false

  $scope.dragStart = ->
    $scope.dragging = true

  $scope.dragEnd = ->
    $scope.dragging = false

  $scope.addMetric = (metric) ->
    MetricsService.addMetric($scope.jobid, $scope.nodeid, metric.id)
    loadMetrics()

  $scope.removeMetric = (metricId) ->
    MetricsService.removeMetric($scope.jobid, $scope.nodeid, metricId)
    loadMetrics()

  $scope.$on 'reload', (event) ->
    console.log 'JobPlanMetricsController'
    loadMetrics() if $scope.nodeid and !$scope.dragging

  loadMetrics() if $scope.nodeid

# --------------------------------------
