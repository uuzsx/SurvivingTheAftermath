// Test fixtures only. Never packaged with the mod.
AftermathEvents.start(event => {
    if (event.module.moduleName == 'regression_cancel_start') event.cancel();
});
AftermathEvents.ready(event => {
    if (event.module.moduleName == 'regression_cancel_ready') event.cancel();
});
AftermathEvents.celebrating(event => {
    if (event.module.moduleName == 'regression_cancel_celebrating') event.cancel();
});
