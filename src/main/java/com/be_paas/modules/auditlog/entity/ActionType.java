package com.be_paas.modules.auditlog.entity;

public enum ActionType {

    // ==================================== USER ====================================

    CREATE_USER,
    UPDATE_USER,
    DELETE_USER,
    RESET_PASSWORD,
    CHANGE_PASSWORD,

    // Change status user
    BAN_USER,
    UNBAN_USER,

    // Update role
    GRANT_ADMIN,
    REVOKE_ADMIN,

    // ==================================== PROJECT ====================================

    CREATE_PROJECT,
    DELETE_PROJECT,
    FORCE_STOP,
    //    MAP_DOMAIN

    // === ENV ===
    CREATE_ENV,
    UPDATE_ENV,
    DELETE_ENV,

}
