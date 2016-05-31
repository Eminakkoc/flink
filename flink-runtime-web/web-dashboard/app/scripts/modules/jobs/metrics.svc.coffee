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

.service 'MetricsService', ($http, $q, flinkConfig) ->
  console.log 'MetricsService'

  @metrics = {}
  @values = {}

  @getWindow = ->
    100

  @setupLS = ->
    if !localStorage.flinkMetrics?
      @saveSetup()

    @metrics = JSON.parse(localStorage.flinkMetrics)

  @saveSetup = ->
    localStorage.flinkMetrics = JSON.stringify(@metrics)

  @saveValue = (jobid, nodeid, value) ->
    unless @values[jobid]?
      @values[jobid] = {}

    unless @values[jobid][nodeid]?
      @values[jobid][nodeid] = []

    @values[jobid][nodeid].push(value)

    if @values[jobid][nodeid].length > @getWindow()
      @values[jobid][nodeid].shift()

  @getValues = (jobid, nodeid, metricid) ->
    return [] unless @values[jobid]?
    return [] unless @values[jobid][nodeid]?

    results = []
    angular.forEach @values[jobid][nodeid], (v, k) =>
      if v.values[metricid]?
        results.push {
          x: v.timestamp
          y: v.values[metricid]
        }

    results

  @setupLSFor = (jobid, nodeid) ->
    if !@metrics[jobid]?
      @metrics[jobid] = {}

    if !@metrics[jobid][nodeid]?
      @metrics[jobid][nodeid] = []

  @addMetric = (jobid, nodeid, metricid) ->
    @setupLSFor(jobid, nodeid)

    @metrics[jobid][nodeid].push(metricid)

    @saveSetup()

  @removeMetric = (jobid, nodeid, metricid) =>
    if @metrics[jobid][nodeid]?
      i = @metrics[jobid][nodeid].indexOf(metricid)
      @metrics[jobid][nodeid].splice(i, 1) if i != -1

      @saveSetup()

  @orderMetrics = (jobid, nodeid, item, index) ->
    @setupLSFor(jobid, nodeid)

    angular.forEach @metrics[jobid][nodeid], (v, k) =>
      if v == item
        @metrics[jobid][nodeid].splice(k, 1)
        if k < index
          index = index - 1

    @metrics[jobid][nodeid].splice(index, 0, item)

    @saveSetup()

  @getMetricsSetup = (jobid, nodeid) =>
    fl = []
    angular.forEach @metrics[jobid][nodeid], (v, k) =>
      fl.push {
        name: v
      }

    {
      names: @metrics[jobid][nodeid]
      list: fl
    }

  @getAvailableMetrics = (jobid, nodeid) =>
    @setupLSFor(jobid, nodeid)

    deferred = $q.defer()

    $http.get flinkConfig.jobServer + "jobs/" + jobid + "/vertices/" + nodeid + "/metrics"
    .success (data) =>
      results = []
      angular.forEach data.available, (v, k) =>
        if @metrics[jobid][nodeid].indexOf(v.id) == -1
          results.push(v)

      deferred.resolve(results)

    deferred.promise

  @getMetrics = (jobid, nodeid, metricIds) ->
    deferred = $q.defer()

    ids = metricIds.join(",")

    $http.get flinkConfig.jobServer + "jobs/" + jobid + "/vertices/" + nodeid + "/metrics?get=" + ids
    .success (data) =>
      result = {}
      angular.forEach data, (v, k) ->
        result[v.id] = parseInt(v.value)

      newValue = {
        timestamp: Date.now()
        values: result
      }
      @saveValue(jobid, nodeid, newValue)
      deferred.resolve(newValue)

    deferred.promise

  @setupLS()

  @
