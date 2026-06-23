package com.be_paas.modules.auditlog.entity;

public enum ActionType {

    // ==================================== USER ====================================

    CREATE_USER,
    UPDATE_USER,
    SOFT_DELETE_USER,
    RESET_PASSWORD,
    CHANGE_PASSWORD,

    // Change status user
    BAN_USER,
    UNBAN_USER,

    // Update role
    GRANT_ADMIN,
    REVOKE_ADMIN

    // ==================================== PROJECT ====================================

//    CREATE_PROJECT,
//    DELETE_PROJECT,
//    FORCE_STOP,
//    UPDATE_ENV,
//    MAP_DOMAIN

}
