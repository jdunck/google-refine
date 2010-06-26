function ReconDialog(column, types) {
    this._column = column;
    this._serviceRecords = [];
    this._selectedServiceRecordIndex = -1;
    
    this._createDialog();
}

ReconDialog.prototype._createDialog = function() {
    var self = this;
    var dialog = $(DOM.loadHTML("core", "scripts/reconciliation/recon-dialog.html"));

    this._elmts = DOM.bind(dialog);
    this._elmts.dialogHeader.text('Reconcile column "' + this._column.name + '"');
    
    this._elmts.addStandardServiceButton.click(function() { self._onAddStandardService(); });
    this._elmts.addNamespacedServiceButton.click(function() { self._onAddNamespacedService(); });
    
    this._elmts.reconcileButton.click(function() { self._onOK(); });
    this._elmts.cancelButton.click(function() { self._dismiss(); });
    
    this._level = DialogSystem.showDialog(dialog);
    this._populateDialog();
};

ReconDialog.prototype._onOK = function() {
    if (this._selectedServiceRecordIndex >= 0) {
        var record = this._serviceRecords[this._selectedServiceRecordIndex];
        if (record.handler) {
            record.handler.start();
        }
    }
    this._dismiss();
};

ReconDialog.prototype._dismiss = function() {
    for (var i = 0; i < this._serviceRecords.length; i++) {
        var record = this._serviceRecords[i];
        if (record.handler) {
            record.handler.dispose();
        }
    }
    this._serviceRecords = null;
    
    DialogSystem.dismissUntil(this._level - 1);
};

ReconDialog.prototype._cleanDialog = function() {
    for (var i = 0; i < this._serviceRecords.length; i++) {
        var record = this._serviceRecords[i];
        if (record.handler) {
            record.handler.deactivate();
        }
        record.selector.remove();
    }
    this._serviceRecords = [];
    this._selectedServiceRecordIndex = -1;
};

ReconDialog.prototype._populateDialog = function() {
    var self = this;
    
    var services = ReconciliationManager.getAllServices();
    if (services.length > 0) {
        var renderService = function(service) {
            var record = {
                service: service,
                handler: null
            };
        
            record.selector = $('<a>')
                .attr("href", "javascript:{}")
                .addClass("recon-dialog-service-selector")
                .text(service.name)
                .appendTo(self._elmts.serviceList)
                .click(function() {
                    self._selectService(record);
                });
                
            $('<a>')
                .html("&nbsp;")
                .addClass("recon-dialog-service-selector-remove")
                .prependTo(record.selector)
                .click(function() {
                    ReconciliationManager.unregisterService(service, function() {
                        self._refresh(-1);
                    });
                });
            
            self._serviceRecords.push(record);
        };
    
        for (var i = 0; i < services.length; i++) {
            renderService(services[i]);
        }
    }
};

ReconDialog.prototype._selectService = function(record) {
    for (var i = 0; i < this._serviceRecords.length; i++) {
        if (record === this._serviceRecords[i]) {
            if (i !== this._selectedServiceRecordIndex) {
                if (this._selectedServiceRecordIndex >= 0) {
                    var oldRecord = this._serviceRecords[this._selectedServiceRecordIndex];
                    if (oldRecord.handler) {
                        oldRecord.selector.removeClass("selected");
                        oldRecord.handler.deactivate();
                    }
                }
                
                this._elmts.servicePanelMessage.hide();
                
                record.selector.addClass("selected");
                if (record.handler) {
                    record.handler.activate();
                } else {
                    var handlerConstructor = eval(record.service.ui.handler);
                    
                    record.handler = new handlerConstructor(
                        this._column, record.service, this._elmts.servicePanelContainer);
                }
                
                this._selectedServiceRecordIndex = i;
                return;
            }
        }
    }
};

ReconDialog.prototype._refresh = function(newSelectIndex) {
    this._cleanDialog();
    this._populateDialog();
    if (newSelectIndex >= 0) {
        this._selectService(this._serviceRecords[newSelectIndex]);
    }
};

ReconDialog.prototype._onAddStandardService = function() {
    var self = this;
    var dialog = $(DOM.loadHTML("core", "scripts/reconciliation/add-standard-service-dialog.html"));
    var elmts = DOM.bind(dialog);
    
    var level = DialogSystem.showDialog(dialog);
    var dismiss = function() {
        DialogSystem.dismissUntil(level - 1);
    };
    
    elmts.cancelButton.click(dismiss);
    elmts.addButton.click(function() {
        var url = $.trim(elmts.input[0].value);
        if (url.length > 0) {
            ReconciliationManager.registerStandardService(url, function(index) {
                self._refresh(index);
            });
        }
        dismiss();
    });
    elmts.input.focus().select();
};

ReconDialog.prototype._onAddNamespacedService = function() {
    var self = this;
    var dialog = $(DOM.loadHTML("core", "scripts/reconciliation/add-namespaced-service-dialog.html"));
    var elmts = DOM.bind(dialog);
    
    var level = DialogSystem.showDialog(dialog);
    var dismiss = function() {
        DialogSystem.dismissUntil(level - 1);
    };
    
    elmts.namespaceInput
        .suggest({ type: '/type/namespace' })
        .bind("fb-select", function(e, data) {
            elmts.typeInput.focus();
        });
        
    elmts.typeInput.suggestT({ type: '/type/type' });
    
    elmts.cancelButton.click(dismiss);
    elmts.addButton.click(function() {
        var namespaceData = elmts.namespaceInput.data("data.suggest");
        var typeData = elmts.typeInput.data("data.suggest");
        if (namespaceData) {
            var url = "http://standard-reconcile.freebaseapps.com/namespace_reconcile?namespace=" + 
                escape(namespaceData.id);
            if (typeData) {
                url += "&type=" + typeData.id;
            }
            
            ReconciliationManager.registerStandardService(url, function(index) {
                self._refresh(index);
            });
        }
        dismiss();
    });
    elmts.namespaceInput.focus().data("suggest").textchange();
};
