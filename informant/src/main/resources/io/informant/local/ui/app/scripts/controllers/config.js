/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* global informant, Informant, $ */

informant.controller('ConfigCtrl', function ($scope, $http) {

  document.title = 'Configuration | Informant';
  $scope.$parent.title = 'Configuration';
  $scope.$parent.activenav = 'config';

  $scope.pattern = {
    // TODO allow comma as decimal separator (and check out html5 input type="number")
    // tolerant of missing whole (.2) and missing decimal (2.)
    percentage: '/^(0|[1-9][0-9]?|100)?(\\.[0-9]*)?$/',
    // tolerant of commas
    integer: '/^(0|[1-9][0-9]*)$/',
    // missing whole (.2) and missing decimal (2.)
    double: '/^(0|[1-9][0-9]*)?(\\.[0-9]*)?$/'
  };

  $scope.saveGeneralConfig = function (deferred) {
    $http.post('backend/config/general', $scope.config.generalConfig)
        .success(function (data) {
          $scope.config.generalConfig.version = data;
          $scope.generalEnabled = $scope.config.generalConfig.enabled;
          deferred.resolve('Saved');
        })
        .error(function (data, status) {
          if (status === 0) {
            deferred.reject('Unable to connect to server');
          } else {
            deferred.reject('An error occurred');
          }
        });
  };

  $scope.saveCoarseProfilingConfig = function (deferred) {
    $http.post('backend/config/coarse-profiling', $scope.config.coarseProfilingConfig)
        .success(function (data) {
          $scope.config.coarseProfilingConfig.version = data;
          $scope.coarseEnabled = $scope.config.coarseProfilingConfig.enabled;
          deferred.resolve('Saved');
        })
        .error(function (data, status) {
          if (status === 0) {
            deferred.reject('Unable to connect to server');
          } else {
            deferred.reject('An error occurred');
          }
        });
  };

  $scope.saveFineProfilingConfig = function (deferred) {
    $http.post('backend/config/fine-profiling', $scope.config.fineProfilingConfig)
        .success(function (data) {
          $scope.config.fineProfilingConfig.version = data;
          $scope.fineEnabled = $scope.config.fineProfilingConfig.enabled;
          deferred.resolve('Saved');
        })
        .error(function (data, status) {
          if (status === 0) {
            deferred.reject('Unable to connect to server');
          } else {
            deferred.reject('An error occurred');
          }
        });
  };

  $scope.saveUserOverridesConfig = function (deferred) {
    $http.post('backend/config/user-overrides', $scope.config.userOverridesConfig)
        .success(function (data) {
          $scope.config.userOverridesConfig.version = data;
          $scope.userEnabled = $scope.config.userOverridesConfig.enabled;
          deferred.resolve('Saved');
        })
        .error(function (data, status) {
          if (status === 0) {
            deferred.reject('Unable to connect to server');
          } else {
            deferred.reject('An error occurred');
          }
        });
  };

  $scope.saveStorageConfig = function (deferred) {
    $http.post('backend/config/storage', $scope.config.storageConfig)
        .success(function (data) {
          $scope.config.storageConfig.version = data;
          deferred.resolve('Saved');
        })
        .error(function (data, status) {
          if (status === 0) {
            deferred.reject('Unable to connect to server');
          } else {
            deferred.reject('An error occurred');
          }
        });
  };

  $scope.deleteAll = function (deferred) {
    $http.post('backend/admin/data/delete-all')
        .success(function (data) {
          deferred.resolve('Deleted');
        })
        .error(function (data, status) {
          if (status === 0) {
            deferred.reject('Unable to connect to server');
          } else {
            deferred.reject('An error occurred');
          }
        });
  };

  $scope.savePluginConfig = function (deferred, pluginIndex) {
    var plugin = $scope.plugins[pluginIndex];
    var properties = {};
    var i;
    for (i = 0; i < plugin.descriptor.properties.length; i++) {
      var property = plugin.descriptor.properties[i];
      if (property.type === 'double') {
        properties[property.name] = parseFloat(property.value);
      } else {
        properties[property.name] = property.value;
      }
    }
    var config = {
      'enabled': plugin.config.enabled,
      'properties': properties,
      'version': plugin.config.version
    };
    $http.post('backend/config/plugin/' + plugin.id, config)
        .success(function (data) {
          plugin.config.version = data;
          plugin.enabled = plugin.config.enabled;
          deferred.resolve('Saved');
        })
        .error(function (data, status) {
          if (status === 0) {
            deferred.reject('Unable to connect to server');
          } else {
            deferred.reject('An error occurred');
          }
        });
  };

  Informant.configureAjaxError();
  // TODO fix initial load spinner
  Informant.showSpinner('#initialLoadSpinner');
  $http.get('backend/config')
      .success(function (data) {
        Informant.hideSpinner('#initialLoadSpinner');

        $scope.config = data;
        $scope.plugins = [];
        var i, j;
        for (i = 0; i < data.pluginDescriptors.length; i++) {
          var plugin = {};
          plugin.descriptor = data.pluginDescriptors[i];
          plugin.id = plugin.descriptor.groupId + ':' + plugin.descriptor.artifactId;
          plugin.config = data.pluginConfigs[plugin.id];
          for (j = 0; j < plugin.descriptor.properties.length; j++) {
            var property = plugin.descriptor.properties[j];
            property.value = plugin.config.properties[property.name];
          }
          $scope.plugins.push(plugin);
        }

        $scope.generalEnabled = $scope.config.generalConfig.enabled;
        $scope.coarseEnabled = $scope.config.coarseProfilingConfig.enabled;
        $scope.fineEnabled = $scope.config.fineProfilingConfig.enabled;
        $scope.userEnabled = $scope.config.userOverridesConfig.enabled;
        for (i = 0; i < $scope.plugins.length; i++) {
          $scope.plugins[i].enabled = $scope.plugins[i].config.enabled;
        }

        var $offscreenMeasure = $('#offscreenMeasure');
        $offscreenMeasure.text(data.dataDir);
        $scope.dataDirControlWidth = ($offscreenMeasure.width() + 50) + 'px';

        // set up calculated properties
        $scope.data = {};
        $scope.data.snapshotExpirationDays = data.storageConfig.snapshotExpirationHours / 24;
        $scope.$watch('data.snapshotExpirationDays', function (newValue) {
          data.storageConfig.snapshotExpirationHours = newValue * 24;
        });
        if (data.fineProfilingConfig.storeThresholdMillis !== -1) {
          $scope.data.fineStoreThresholdOverride = true;
          $scope.data.fineStoreThresholdMillis = data.fineProfilingConfig.storeThresholdMillis;
        } else {
          $scope.data.fineStoreThresholdOverride = false;
          $scope.data.fineStoreThresholdMillis = '';
        }
        $scope.$watch('[data.fineStoreThresholdOverride, data.fineStoreThresholdMillis]',
            function (newValue) {
              if (newValue[0]) {
                if ($scope.data.fineStoreThresholdMillis === '') {
                  $scope.data.fineStoreThresholdMillis =
                      $scope.config.generalConfig.storeThresholdMillis;
                }
                data.fineProfilingConfig.storeThresholdMillis = $scope.data.fineStoreThresholdMillis;
              } else {
                $scope.data.fineStoreThresholdMillis = '';
                data.fineProfilingConfig.storeThresholdMillis = -1;
              }
            }, true);
      })
      .error(function (error) {
        // TODO
      });
});

informant.directive('ixOnOff', function () {
  return {
    scope: {
      ixModel: '&',
      ixDisabled: '&'
    },
    templateUrl: 'template/ix-on-off.html'
  };
});
