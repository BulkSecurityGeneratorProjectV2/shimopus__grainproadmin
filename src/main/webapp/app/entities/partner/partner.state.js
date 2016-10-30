(function() {
    'use strict';

    angular
        .module('grainAdminApp')
        .config(stateConfig);

    stateConfig.$inject = ['$stateProvider'];

    function stateConfig($stateProvider) {
        $stateProvider
        .state('partner', {
            parent: 'entity',
            url: '/partner?page&sort&search',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'grainAdminApp.partner.home.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/partner/partners.html',
                    controller: 'PartnerController',
                    controllerAs: 'vm'
                }
            },
            params: {
                page: {
                    value: '1',
                    squash: true
                },
                sort: {
                    value: 'id,asc',
                    squash: true
                },
                search: null
            },
            resolve: {
                pagingParams: ['$stateParams', 'PaginationUtil', function ($stateParams, PaginationUtil) {
                    return {
                        page: PaginationUtil.parsePage($stateParams.page),
                        sort: $stateParams.sort,
                        predicate: PaginationUtil.parsePredicate($stateParams.sort),
                        ascending: PaginationUtil.parseAscending($stateParams.sort),
                        search: $stateParams.search
                    };
                }],
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('partner');
                    $translatePartialLoader.addPart('nDS');
                    $translatePartialLoader.addPart('global');
                    return $translate.refresh();
                }]
            }
        })
        .state('partner-detail', {
            parent: 'entity',
            url: '/partner/{id}',
            data: {
                authorities: ['ROLE_USER'],
                pageTitle: 'grainAdminApp.partner.detail.title'
            },
            views: {
                'content@': {
                    templateUrl: 'app/entities/partner/partner-detail.html',
                    controller: 'PartnerDetailController',
                    controllerAs: 'vm'
                }
            },
            resolve: {
                translatePartialLoader: ['$translate', '$translatePartialLoader', function ($translate, $translatePartialLoader) {
                    $translatePartialLoader.addPart('partner');
                    $translatePartialLoader.addPart('nDS');
                    return $translate.refresh();
                }],
                entity: ['$stateParams', 'Partner', function($stateParams, Partner) {
                    return Partner.get({id : $stateParams.id}).$promise;
                }],
                previousState: ["$state", function ($state) {
                    var currentStateData = {
                        name: $state.current.name || 'partner',
                        params: $state.params,
                        url: $state.href($state.current.name, $state.params)
                    };
                    return currentStateData;
                }]
            }
        })
        .state('partner-detail.edit', {
            parent: 'partner-detail',
            url: '/detail/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/partner/partner-dialog.html',
                    controller: 'PartnerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Partner', function(Partner) {
                            return Partner.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('^', {}, { reload: false });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('partner.new', {
            parent: 'partner',
            url: '/new',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/partner/partner-dialog.html',
                    controller: 'PartnerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: function () {
                            return {
                                name: null,
                                shortName: null,
                                inn: null,
                                nds: null,
                                card: null,
                                lastUpdate: null,
                                id: null,
                                servicePrices: [],
                                ownedBies: []
                            };
                        }
                    }
                }).result.then(function() {
                    $state.go('partner', null, { reload: 'partner' });
                }, function() {
                    $state.go('partner');
                });
            }]
        })
        .state('partner.edit', {
            parent: 'partner',
            url: '/{id}/edit',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/partner/partner-dialog.html',
                    controller: 'PartnerDialogController',
                    controllerAs: 'vm',
                    backdrop: 'static',
                    size: 'lg',
                    resolve: {
                        entity: ['Partner', function(Partner) {
                            return Partner.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('partner', null, { reload: 'partner' });
                }, function() {
                    $state.go('^');
                });
            }]
        })
        .state('partner.delete', {
            parent: 'partner',
            url: '/{id}/delete',
            data: {
                authorities: ['ROLE_USER']
            },
            onEnter: ['$stateParams', '$state', '$uibModal', function($stateParams, $state, $uibModal) {
                $uibModal.open({
                    templateUrl: 'app/entities/partner/partner-delete-dialog.html',
                    controller: 'PartnerDeleteController',
                    controllerAs: 'vm',
                    size: 'md',
                    resolve: {
                        entity: ['Partner', function(Partner) {
                            return Partner.get({id : $stateParams.id}).$promise;
                        }]
                    }
                }).result.then(function() {
                    $state.go('partner', null, { reload: 'partner' });
                }, function() {
                    $state.go('^');
                });
            }]
        });
    }

})();
