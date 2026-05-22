# lark-mcp 全部 Tools 清单

> 来源：[tools-en.md](https://github.com/larksuite/lark-openapi-mcp/blob/main/docs/reference/tool-presets/tools-en.md)
> npm 包：@larksuiteoapi/lark-mcp（飞书/Lark 官方 OpenAPI MCP）
> 共 **1271** 个 OpenAPI tool，**61** 个业务域。
> OpenClaw 调用名（`-c snake`）：`lark-mcp__` + 将 `.` 换为 `_`（保留 camelCase 段，如 `appTableRecord`）。
> 仅 -t 白名单中的 tool 会注册；未配置则不会出现在 openclaw mcp list。

- 本项目已启用 subset：[LARK-MCP-TOOLS.md](./LARK-MCP-TOOLS.md)
- 预设集合：[presets.md](https://github.com/larksuite/lark-openapi-mcp/blob/main/docs/reference/tool-presets/presets.md)

## 预设（preset）一览

| preset | 说明 | 约 tool 数 |
|--------|------|-----------|
| preset.light | 轻量：消息/文档/多维表检索等 | ~10 |
| preset.default | 默认：IM + 多维表 CRUD + 文档 + 通讯录 | ~19 |
| preset.im.default | 即时消息 | 5 |
| preset.base.default | 多维表基础 | 7 |
| preset.base.batch | 多维表批量 | 7 |
| preset.doc.default | 文档/Wiki/权限 | 6 |
| preset.task.default | 任务 | 4 |
| preset.calendar.default | 日历 | 5 |

## 按业务域统计

| 业务域 | 数量 |
|--------|------|
| acsV1 | 11 |
| adminV1 | 13 |
| ailyV1 | 19 |
| apaasV1 | 37 |
| applicationV5 | 2 |
| applicationV6 | 25 |
| approvalV4 | 29 |
| attendanceV1 | 37 |
| authV3 | 5 |
| authenV1 | 1 |
| baikeV1 | 11 |
| baseV2 | 3 |
| bitableV1 | 46 |
| boardV1 | 1 |
| calendarV4 | 41 |
| cardkitV1 | 10 |
| compensationV1 | 6 |
| contactV3 | 70 |
| corehrV1 | 103 |
| corehrV2 | 115 |
| directoryV1 | 21 |
| docsV1 | 1 |
| docxV1 | 19 |
| driveV1 | 49 |
| driveV2 | 3 |
| ehrV1 | 1 |
| eventV1 | 1 |
| helpdeskV1 | 8 |
| hireV1 | 175 |
| hireV2 | 3 |
| humanAuthenticationV1 | 1 |
| imV1 | 54 |
| imV2 | 12 |
| lingoV1 | 12 |
| mailV1 | 67 |
| mdmV1 | 2 |
| mdmV3 | 2 |
| minutesV1 | 3 |
| momentsV1 | 1 |
| okrV1 | 11 |
| opticalCharRecognitionV1 | 1 |
| passportV1 | 2 |
| payrollV1 | 6 |
| performanceV1 | 4 |
| performanceV2 | 16 |
| personalSettingsV1 | 6 |
| reportV1 | 3 |
| searchV2 | 14 |
| securityAndComplianceV1 | 1 |
| sheetsV3 | 27 |
| speechToTextV1 | 2 |
| taskV1 | 23 |
| taskV2 | 51 |
| tenantV2 | 2 |
| translationV1 | 2 |
| trustPartyV1 | 5 |
| vcV1 | 55 |
| verificationV1 | 1 |
| wikiV1 | 1 |
| wikiV2 | 15 |
| workplaceV1 | 3 |
| **合计** | **1271** |

---

## acsV1 (11)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| acs.v1.accessRecord.list | acs_v1_accessRecord_list | lark-mcp__acs_v1_accessRecord_list |
| acs.v1.device.list | acs_v1_device_list | lark-mcp__acs_v1_device_list |
| acs.v1.ruleExternal.create | acs_v1_ruleExternal_create | lark-mcp__acs_v1_ruleExternal_create |
| acs.v1.ruleExternal.delete | acs_v1_ruleExternal_delete | lark-mcp__acs_v1_ruleExternal_delete |
| acs.v1.ruleExternal.deviceBind | acs_v1_ruleExternal_deviceBind | lark-mcp__acs_v1_ruleExternal_deviceBind |
| acs.v1.ruleExternal.get | acs_v1_ruleExternal_get | lark-mcp__acs_v1_ruleExternal_get |
| acs.v1.user.get | acs_v1_user_get | lark-mcp__acs_v1_user_get |
| acs.v1.user.list | acs_v1_user_list | lark-mcp__acs_v1_user_list |
| acs.v1.user.patch | acs_v1_user_patch | lark-mcp__acs_v1_user_patch |
| acs.v1.visitor.create | acs_v1_visitor_create | lark-mcp__acs_v1_visitor_create |
| acs.v1.visitor.delete | acs_v1_visitor_delete | lark-mcp__acs_v1_visitor_delete |

## adminV1 (13)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| admin.v1.adminDeptStat.list | admin_v1_adminDeptStat_list | lark-mcp__admin_v1_adminDeptStat_list |
| admin.v1.adminUserStat.list | admin_v1_adminUserStat_list | lark-mcp__admin_v1_adminUserStat_list |
| admin.v1.auditInfo.list | admin_v1_auditInfo_list | lark-mcp__admin_v1_auditInfo_list |
| admin.v1.badge.create | admin_v1_badge_create | lark-mcp__admin_v1_badge_create |
| admin.v1.badge.get | admin_v1_badge_get | lark-mcp__admin_v1_badge_get |
| admin.v1.badge.list | admin_v1_badge_list | lark-mcp__admin_v1_badge_list |
| admin.v1.badge.update | admin_v1_badge_update | lark-mcp__admin_v1_badge_update |
| admin.v1.badgeGrant.create | admin_v1_badgeGrant_create | lark-mcp__admin_v1_badgeGrant_create |
| admin.v1.badgeGrant.delete | admin_v1_badgeGrant_delete | lark-mcp__admin_v1_badgeGrant_delete |
| admin.v1.badgeGrant.get | admin_v1_badgeGrant_get | lark-mcp__admin_v1_badgeGrant_get |
| admin.v1.badgeGrant.list | admin_v1_badgeGrant_list | lark-mcp__admin_v1_badgeGrant_list |
| admin.v1.badgeGrant.update | admin_v1_badgeGrant_update | lark-mcp__admin_v1_badgeGrant_update |
| admin.v1.password.reset | admin_v1_password_reset | lark-mcp__admin_v1_password_reset |

## ailyV1 (19)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| aily.v1.ailySession.create | aily_v1_ailySession_create | lark-mcp__aily_v1_ailySession_create |
| aily.v1.ailySession.delete | aily_v1_ailySession_delete | lark-mcp__aily_v1_ailySession_delete |
| aily.v1.ailySession.get | aily_v1_ailySession_get | lark-mcp__aily_v1_ailySession_get |
| aily.v1.ailySession.update | aily_v1_ailySession_update | lark-mcp__aily_v1_ailySession_update |
| aily.v1.ailySessionAilyMessage.create | aily_v1_ailySessionAilyMessage_create | lark-mcp__aily_v1_ailySessionAilyMessage_create |
| aily.v1.ailySessionAilyMessage.get | aily_v1_ailySessionAilyMessage_get | lark-mcp__aily_v1_ailySessionAilyMessage_get |
| aily.v1.ailySessionAilyMessage.list | aily_v1_ailySessionAilyMessage_list | lark-mcp__aily_v1_ailySessionAilyMessage_list |
| aily.v1.ailySessionRun.cancel | aily_v1_ailySessionRun_cancel | lark-mcp__aily_v1_ailySessionRun_cancel |
| aily.v1.ailySessionRun.create | aily_v1_ailySessionRun_create | lark-mcp__aily_v1_ailySessionRun_create |
| aily.v1.ailySessionRun.get | aily_v1_ailySessionRun_get | lark-mcp__aily_v1_ailySessionRun_get |
| aily.v1.ailySessionRun.list | aily_v1_ailySessionRun_list | lark-mcp__aily_v1_ailySessionRun_list |
| aily.v1.appDataAsset.create | aily_v1_appDataAsset_create | lark-mcp__aily_v1_appDataAsset_create |
| aily.v1.appDataAsset.delete | aily_v1_appDataAsset_delete | lark-mcp__aily_v1_appDataAsset_delete |
| aily.v1.appDataAsset.get | aily_v1_appDataAsset_get | lark-mcp__aily_v1_appDataAsset_get |
| aily.v1.appDataAsset.list | aily_v1_appDataAsset_list | lark-mcp__aily_v1_appDataAsset_list |
| aily.v1.appDataAssetTag.list | aily_v1_appDataAssetTag_list | lark-mcp__aily_v1_appDataAssetTag_list |
| aily.v1.appSkill.get | aily_v1_appSkill_get | lark-mcp__aily_v1_appSkill_get |
| aily.v1.appSkill.list | aily_v1_appSkill_list | lark-mcp__aily_v1_appSkill_list |
| aily.v1.appSkill.start | aily_v1_appSkill_start | lark-mcp__aily_v1_appSkill_start |

## apaasV1 (37)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| apaas.v1.app.list | apaas_v1_app_list | lark-mcp__apaas_v1_app_list |
| apaas.v1.applicationAuditLog.auditLogList | apaas_v1_applicationAuditLog_auditLogList | lark-mcp__apaas_v1_applicationAuditLog_auditLogList |
| apaas.v1.applicationAuditLog.dataChangeLogDetail | apaas_v1_applicationAuditLog_dataChangeLogDetail | lark-mcp__apaas_v1_applicationAuditLog_dataChangeLogDetail |
| apaas.v1.applicationAuditLog.dataChangeLogsList | apaas_v1_applicationAuditLog_dataChangeLogsList | lark-mcp__apaas_v1_applicationAuditLog_dataChangeLogsList |
| apaas.v1.applicationAuditLog.get | apaas_v1_applicationAuditLog_get | lark-mcp__apaas_v1_applicationAuditLog_get |
| apaas.v1.applicationEnvironmentVariable.get | apaas_v1_applicationEnvironmentVariable_get | lark-mcp__apaas_v1_applicationEnvironmentVariable_get |
| apaas.v1.applicationEnvironmentVariable.query | apaas_v1_applicationEnvironmentVariable_query | lark-mcp__apaas_v1_applicationEnvironmentVariable_query |
| apaas.v1.applicationFlow.execute | apaas_v1_applicationFlow_execute | lark-mcp__apaas_v1_applicationFlow_execute |
| apaas.v1.applicationFunction.invoke | apaas_v1_applicationFunction_invoke | lark-mcp__apaas_v1_applicationFunction_invoke |
| apaas.v1.applicationObject.oqlQuery | apaas_v1_applicationObject_oqlQuery | lark-mcp__apaas_v1_applicationObject_oqlQuery |
| apaas.v1.applicationObject.search | apaas_v1_applicationObject_search | lark-mcp__apaas_v1_applicationObject_search |
| apaas.v1.applicationObjectRecord.batchCreate | apaas_v1_applicationObjectRecord_batchCreate | lark-mcp__apaas_v1_applicationObjectRecord_batchCreate |
| apaas.v1.applicationObjectRecord.batchDelete | apaas_v1_applicationObjectRecord_batchDelete | lark-mcp__apaas_v1_applicationObjectRecord_batchDelete |
| apaas.v1.applicationObjectRecord.batchQuery | apaas_v1_applicationObjectRecord_batchQuery | lark-mcp__apaas_v1_applicationObjectRecord_batchQuery |
| apaas.v1.applicationObjectRecord.batchUpdate | apaas_v1_applicationObjectRecord_batchUpdate | lark-mcp__apaas_v1_applicationObjectRecord_batchUpdate |
| apaas.v1.applicationObjectRecord.create | apaas_v1_applicationObjectRecord_create | lark-mcp__apaas_v1_applicationObjectRecord_create |
| apaas.v1.applicationObjectRecord.delete | apaas_v1_applicationObjectRecord_delete | lark-mcp__apaas_v1_applicationObjectRecord_delete |
| apaas.v1.applicationObjectRecord.patch | apaas_v1_applicationObjectRecord_patch | lark-mcp__apaas_v1_applicationObjectRecord_patch |
| apaas.v1.applicationObjectRecord.query | apaas_v1_applicationObjectRecord_query | lark-mcp__apaas_v1_applicationObjectRecord_query |
| apaas.v1.applicationRecordPermissionMember.batchCreateAuthorization | apaas_v1_applicationRecordPermissionMember_batchCreateAuthorization | lark-mcp__apaas_v1_applicationRecordPermissionMember_batchCreateAuthorization |
| apaas.v1.applicationRecordPermissionMember.batchRemoveAuthorization | apaas_v1_applicationRecordPermissionMember_batchRemoveAuthorization | lark-mcp__apaas_v1_applicationRecordPermissionMember_batchRemoveAuthorization |
| apaas.v1.applicationRoleMember.batchCreateAuthorization | apaas_v1_applicationRoleMember_batchCreateAuthorization | lark-mcp__apaas_v1_applicationRoleMember_batchCreateAuthorization |
| apaas.v1.applicationRoleMember.batchRemoveAuthorization | apaas_v1_applicationRoleMember_batchRemoveAuthorization | lark-mcp__apaas_v1_applicationRoleMember_batchRemoveAuthorization |
| apaas.v1.applicationRoleMember.get | apaas_v1_applicationRoleMember_get | lark-mcp__apaas_v1_applicationRoleMember_get |
| apaas.v1.approvalInstance.cancel | apaas_v1_approvalInstance_cancel | lark-mcp__apaas_v1_approvalInstance_cancel |
| apaas.v1.approvalTask.addAssignee | apaas_v1_approvalTask_addAssignee | lark-mcp__apaas_v1_approvalTask_addAssignee |
| apaas.v1.approvalTask.agree | apaas_v1_approvalTask_agree | lark-mcp__apaas_v1_approvalTask_agree |
| apaas.v1.approvalTask.reject | apaas_v1_approvalTask_reject | lark-mcp__apaas_v1_approvalTask_reject |
| apaas.v1.approvalTask.transfer | apaas_v1_approvalTask_transfer | lark-mcp__apaas_v1_approvalTask_transfer |
| apaas.v1.seatActivity.list | apaas_v1_seatActivity_list | lark-mcp__apaas_v1_seatActivity_list |
| apaas.v1.seatAssignment.list | apaas_v1_seatAssignment_list | lark-mcp__apaas_v1_seatAssignment_list |
| apaas.v1.userTask.cc | apaas_v1_userTask_cc | lark-mcp__apaas_v1_userTask_cc |
| apaas.v1.userTask.chatGroup | apaas_v1_userTask_chatGroup | lark-mcp__apaas_v1_userTask_chatGroup |
| apaas.v1.userTask.expediting | apaas_v1_userTask_expediting | lark-mcp__apaas_v1_userTask_expediting |
| apaas.v1.userTask.query | apaas_v1_userTask_query | lark-mcp__apaas_v1_userTask_query |
| apaas.v1.userTask.rollback | apaas_v1_userTask_rollback | lark-mcp__apaas_v1_userTask_rollback |
| apaas.v1.userTask.rollbackPoints | apaas_v1_userTask_rollbackPoints | lark-mcp__apaas_v1_userTask_rollbackPoints |

## applicationV5 (2)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| application.v5.application.favourite | application_v5_application_favourite | lark-mcp__application_v5_application_favourite |
| application.v5.application.recommend | application_v5_application_recommend | lark-mcp__application_v5_application_recommend |

## applicationV6 (25)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| application.v6.appBadge.set | application_v6_appBadge_set | lark-mcp__application_v6_appBadge_set |
| application.v6.application.contactsRangeConfiguration | application_v6_application_contactsRangeConfiguration | lark-mcp__application_v6_application_contactsRangeConfiguration |
| application.v6.application.get | application_v6_application_get | lark-mcp__application_v6_application_get |
| application.v6.application.list | application_v6_application_list | lark-mcp__application_v6_application_list |
| application.v6.application.patch | application_v6_application_patch | lark-mcp__application_v6_application_patch |
| application.v6.application.underauditlist | application_v6_application_underauditlist | lark-mcp__application_v6_application_underauditlist |
| application.v6.applicationAppUsage.departmentOverview | application_v6_applicationAppUsage_departmentOverview | lark-mcp__application_v6_applicationAppUsage_departmentOverview |
| application.v6.applicationAppUsage.messagePushOverview | application_v6_applicationAppUsage_messagePushOverview | lark-mcp__application_v6_applicationAppUsage_messagePushOverview |
| application.v6.applicationAppUsage.overview | application_v6_applicationAppUsage_overview | lark-mcp__application_v6_applicationAppUsage_overview |
| application.v6.applicationAppVersion.contactsRangeSuggest | application_v6_applicationAppVersion_contactsRangeSuggest | lark-mcp__application_v6_applicationAppVersion_contactsRangeSuggest |
| application.v6.applicationAppVersion.get | application_v6_applicationAppVersion_get | lark-mcp__application_v6_applicationAppVersion_get |
| application.v6.applicationAppVersion.list | application_v6_applicationAppVersion_list | lark-mcp__application_v6_applicationAppVersion_list |
| application.v6.applicationAppVersion.patch | application_v6_applicationAppVersion_patch | lark-mcp__application_v6_applicationAppVersion_patch |
| application.v6.applicationCollaborators.get | application_v6_applicationCollaborators_get | lark-mcp__application_v6_applicationCollaborators_get |
| application.v6.applicationCollaborators.update | application_v6_applicationCollaborators_update | lark-mcp__application_v6_applicationCollaborators_update |
| application.v6.applicationContactsRange.patch | application_v6_applicationContactsRange_patch | lark-mcp__application_v6_applicationContactsRange_patch |
| application.v6.applicationFeedback.list | application_v6_applicationFeedback_list | lark-mcp__application_v6_applicationFeedback_list |
| application.v6.applicationFeedback.patch | application_v6_applicationFeedback_patch | lark-mcp__application_v6_applicationFeedback_patch |
| application.v6.applicationManagement.update | application_v6_applicationManagement_update | lark-mcp__application_v6_applicationManagement_update |
| application.v6.applicationOwner.update | application_v6_applicationOwner_update | lark-mcp__application_v6_applicationOwner_update |
| application.v6.applicationVisibility.checkWhiteBlackList | application_v6_applicationVisibility_checkWhiteBlackList | lark-mcp__application_v6_applicationVisibility_checkWhiteBlackList |
| application.v6.applicationVisibility.patch | application_v6_applicationVisibility_patch | lark-mcp__application_v6_applicationVisibility_patch |
| application.v6.appRecommendRule.list | application_v6_appRecommendRule_list | lark-mcp__application_v6_appRecommendRule_list |
| application.v6.scope.apply | application_v6_scope_apply | lark-mcp__application_v6_scope_apply |
| application.v6.scope.list | application_v6_scope_list | lark-mcp__application_v6_scope_list |

## approvalV4 (29)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| approval.v4.approval.create | approval_v4_approval_create | lark-mcp__approval_v4_approval_create |
| approval.v4.approval.get | approval_v4_approval_get | lark-mcp__approval_v4_approval_get |
| approval.v4.approval.subscribe | approval_v4_approval_subscribe | lark-mcp__approval_v4_approval_subscribe |
| approval.v4.approval.unsubscribe | approval_v4_approval_unsubscribe | lark-mcp__approval_v4_approval_unsubscribe |
| approval.v4.externalApproval.create | approval_v4_externalApproval_create | lark-mcp__approval_v4_externalApproval_create |
| approval.v4.externalApproval.get | approval_v4_externalApproval_get | lark-mcp__approval_v4_externalApproval_get |
| approval.v4.externalInstance.check | approval_v4_externalInstance_check | lark-mcp__approval_v4_externalInstance_check |
| approval.v4.externalInstance.create | approval_v4_externalInstance_create | lark-mcp__approval_v4_externalInstance_create |
| approval.v4.externalTask.list | approval_v4_externalTask_list | lark-mcp__approval_v4_externalTask_list |
| approval.v4.instance.addSign | approval_v4_instance_addSign | lark-mcp__approval_v4_instance_addSign |
| approval.v4.instance.cancel | approval_v4_instance_cancel | lark-mcp__approval_v4_instance_cancel |
| approval.v4.instance.cc | approval_v4_instance_cc | lark-mcp__approval_v4_instance_cc |
| approval.v4.instance.create | approval_v4_instance_create | lark-mcp__approval_v4_instance_create |
| approval.v4.instance.get | approval_v4_instance_get | lark-mcp__approval_v4_instance_get |
| approval.v4.instance.list | approval_v4_instance_list | lark-mcp__approval_v4_instance_list |
| approval.v4.instance.preview | approval_v4_instance_preview | lark-mcp__approval_v4_instance_preview |
| approval.v4.instance.query | approval_v4_instance_query | lark-mcp__approval_v4_instance_query |
| approval.v4.instance.searchCc | approval_v4_instance_searchCc | lark-mcp__approval_v4_instance_searchCc |
| approval.v4.instance.specifiedRollback | approval_v4_instance_specifiedRollback | lark-mcp__approval_v4_instance_specifiedRollback |
| approval.v4.instanceComment.create | approval_v4_instanceComment_create | lark-mcp__approval_v4_instanceComment_create |
| approval.v4.instanceComment.delete | approval_v4_instanceComment_delete | lark-mcp__approval_v4_instanceComment_delete |
| approval.v4.instanceComment.list | approval_v4_instanceComment_list | lark-mcp__approval_v4_instanceComment_list |
| approval.v4.instanceComment.remove | approval_v4_instanceComment_remove | lark-mcp__approval_v4_instanceComment_remove |
| approval.v4.task.approve | approval_v4_task_approve | lark-mcp__approval_v4_task_approve |
| approval.v4.task.query | approval_v4_task_query | lark-mcp__approval_v4_task_query |
| approval.v4.task.reject | approval_v4_task_reject | lark-mcp__approval_v4_task_reject |
| approval.v4.task.resubmit | approval_v4_task_resubmit | lark-mcp__approval_v4_task_resubmit |
| approval.v4.task.search | approval_v4_task_search | lark-mcp__approval_v4_task_search |
| approval.v4.task.transfer | approval_v4_task_transfer | lark-mcp__approval_v4_task_transfer |

## attendanceV1 (37)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| attendance.v1.approvalInfo.process | attendance_v1_approvalInfo_process | lark-mcp__attendance_v1_approvalInfo_process |
| attendance.v1.archiveRule.delReport | attendance_v1_archiveRule_delReport | lark-mcp__attendance_v1_archiveRule_delReport |
| attendance.v1.archiveRule.list | attendance_v1_archiveRule_list | lark-mcp__attendance_v1_archiveRule_list |
| attendance.v1.archiveRule.uploadReport | attendance_v1_archiveRule_uploadReport | lark-mcp__attendance_v1_archiveRule_uploadReport |
| attendance.v1.archiveRule.userStatsFieldsQuery | attendance_v1_archiveRule_userStatsFieldsQuery | lark-mcp__attendance_v1_archiveRule_userStatsFieldsQuery |
| attendance.v1.group.create | attendance_v1_group_create | lark-mcp__attendance_v1_group_create |
| attendance.v1.group.delete | attendance_v1_group_delete | lark-mcp__attendance_v1_group_delete |
| attendance.v1.group.get | attendance_v1_group_get | lark-mcp__attendance_v1_group_get |
| attendance.v1.group.list | attendance_v1_group_list | lark-mcp__attendance_v1_group_list |
| attendance.v1.group.listUser | attendance_v1_group_listUser | lark-mcp__attendance_v1_group_listUser |
| attendance.v1.group.search | attendance_v1_group_search | lark-mcp__attendance_v1_group_search |
| attendance.v1.leaveAccrualRecord.patch | attendance_v1_leaveAccrualRecord_patch | lark-mcp__attendance_v1_leaveAccrualRecord_patch |
| attendance.v1.leaveEmployExpireRecord.get | attendance_v1_leaveEmployExpireRecord_get | lark-mcp__attendance_v1_leaveEmployExpireRecord_get |
| attendance.v1.shift.create | attendance_v1_shift_create | lark-mcp__attendance_v1_shift_create |
| attendance.v1.shift.delete | attendance_v1_shift_delete | lark-mcp__attendance_v1_shift_delete |
| attendance.v1.shift.get | attendance_v1_shift_get | lark-mcp__attendance_v1_shift_get |
| attendance.v1.shift.list | attendance_v1_shift_list | lark-mcp__attendance_v1_shift_list |
| attendance.v1.shift.query | attendance_v1_shift_query | lark-mcp__attendance_v1_shift_query |
| attendance.v1.userApproval.create | attendance_v1_userApproval_create | lark-mcp__attendance_v1_userApproval_create |
| attendance.v1.userApproval.query | attendance_v1_userApproval_query | lark-mcp__attendance_v1_userApproval_query |
| attendance.v1.userDailyShift.batchCreate | attendance_v1_userDailyShift_batchCreate | lark-mcp__attendance_v1_userDailyShift_batchCreate |
| attendance.v1.userDailyShift.batchCreateTemp | attendance_v1_userDailyShift_batchCreateTemp | lark-mcp__attendance_v1_userDailyShift_batchCreateTemp |
| attendance.v1.userDailyShift.query | attendance_v1_userDailyShift_query | lark-mcp__attendance_v1_userDailyShift_query |
| attendance.v1.userFlow.batchCreate | attendance_v1_userFlow_batchCreate | lark-mcp__attendance_v1_userFlow_batchCreate |
| attendance.v1.userFlow.batchDel | attendance_v1_userFlow_batchDel | lark-mcp__attendance_v1_userFlow_batchDel |
| attendance.v1.userFlow.get | attendance_v1_userFlow_get | lark-mcp__attendance_v1_userFlow_get |
| attendance.v1.userFlow.query | attendance_v1_userFlow_query | lark-mcp__attendance_v1_userFlow_query |
| attendance.v1.userSetting.modify | attendance_v1_userSetting_modify | lark-mcp__attendance_v1_userSetting_modify |
| attendance.v1.userSetting.query | attendance_v1_userSetting_query | lark-mcp__attendance_v1_userSetting_query |
| attendance.v1.userStatsData.query | attendance_v1_userStatsData_query | lark-mcp__attendance_v1_userStatsData_query |
| attendance.v1.userStatsField.query | attendance_v1_userStatsField_query | lark-mcp__attendance_v1_userStatsField_query |
| attendance.v1.userStatsView.query | attendance_v1_userStatsView_query | lark-mcp__attendance_v1_userStatsView_query |
| attendance.v1.userStatsView.update | attendance_v1_userStatsView_update | lark-mcp__attendance_v1_userStatsView_update |
| attendance.v1.userTask.query | attendance_v1_userTask_query | lark-mcp__attendance_v1_userTask_query |
| attendance.v1.userTaskRemedy.create | attendance_v1_userTaskRemedy_create | lark-mcp__attendance_v1_userTaskRemedy_create |
| attendance.v1.userTaskRemedy.query | attendance_v1_userTaskRemedy_query | lark-mcp__attendance_v1_userTaskRemedy_query |
| attendance.v1.userTaskRemedy.queryUserAllowedRemedys | attendance_v1_userTaskRemedy_queryUserAllowedRemedys | lark-mcp__attendance_v1_userTaskRemedy_queryUserAllowedRemedys |

## authV3 (5)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| auth.v3.auth.appAccessToken | auth_v3_auth_appAccessToken | lark-mcp__auth_v3_auth_appAccessToken |
| auth.v3.auth.appAccessTokenInternal | auth_v3_auth_appAccessTokenInternal | lark-mcp__auth_v3_auth_appAccessTokenInternal |
| auth.v3.auth.appTicketResend | auth_v3_auth_appTicketResend | lark-mcp__auth_v3_auth_appTicketResend |
| auth.v3.auth.tenantAccessToken | auth_v3_auth_tenantAccessToken | lark-mcp__auth_v3_auth_tenantAccessToken |
| auth.v3.auth.tenantAccessTokenInternal | auth_v3_auth_tenantAccessTokenInternal | lark-mcp__auth_v3_auth_tenantAccessTokenInternal |

## authenV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| authen.v1.userInfo.get | authen_v1_userInfo_get | lark-mcp__authen_v1_userInfo_get |

## baikeV1 (11)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| baike.v1.classification.list | baike_v1_classification_list | lark-mcp__baike_v1_classification_list |
| baike.v1.draft.create | baike_v1_draft_create | lark-mcp__baike_v1_draft_create |
| baike.v1.draft.update | baike_v1_draft_update | lark-mcp__baike_v1_draft_update |
| baike.v1.entity.create | baike_v1_entity_create | lark-mcp__baike_v1_entity_create |
| baike.v1.entity.extract | baike_v1_entity_extract | lark-mcp__baike_v1_entity_extract |
| baike.v1.entity.get | baike_v1_entity_get | lark-mcp__baike_v1_entity_get |
| baike.v1.entity.highlight | baike_v1_entity_highlight | lark-mcp__baike_v1_entity_highlight |
| baike.v1.entity.list | baike_v1_entity_list | lark-mcp__baike_v1_entity_list |
| baike.v1.entity.match | baike_v1_entity_match | lark-mcp__baike_v1_entity_match |
| baike.v1.entity.search | baike_v1_entity_search | lark-mcp__baike_v1_entity_search |
| baike.v1.entity.update | baike_v1_entity_update | lark-mcp__baike_v1_entity_update |

## baseV2 (3)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| base.v2.appRole.create | base_v2_appRole_create | lark-mcp__base_v2_appRole_create |
| base.v2.appRole.list | base_v2_appRole_list | lark-mcp__base_v2_appRole_list |
| base.v2.appRole.update | base_v2_appRole_update | lark-mcp__base_v2_appRole_update |

## bitableV1 (46)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| bitable.v1.app.copy | bitable_v1_app_copy | lark-mcp__bitable_v1_app_copy |
| bitable.v1.app.create | bitable_v1_app_create | lark-mcp__bitable_v1_app_create |
| bitable.v1.app.get | bitable_v1_app_get | lark-mcp__bitable_v1_app_get |
| bitable.v1.app.update | bitable_v1_app_update | lark-mcp__bitable_v1_app_update |
| bitable.v1.appDashboard.copy | bitable_v1_appDashboard_copy | lark-mcp__bitable_v1_appDashboard_copy |
| bitable.v1.appDashboard.list | bitable_v1_appDashboard_list | lark-mcp__bitable_v1_appDashboard_list |
| bitable.v1.appRole.create | bitable_v1_appRole_create | lark-mcp__bitable_v1_appRole_create |
| bitable.v1.appRole.delete | bitable_v1_appRole_delete | lark-mcp__bitable_v1_appRole_delete |
| bitable.v1.appRole.list | bitable_v1_appRole_list | lark-mcp__bitable_v1_appRole_list |
| bitable.v1.appRole.update | bitable_v1_appRole_update | lark-mcp__bitable_v1_appRole_update |
| bitable.v1.appRoleMember.batchCreate | bitable_v1_appRoleMember_batchCreate | lark-mcp__bitable_v1_appRoleMember_batchCreate |
| bitable.v1.appRoleMember.batchDelete | bitable_v1_appRoleMember_batchDelete | lark-mcp__bitable_v1_appRoleMember_batchDelete |
| bitable.v1.appRoleMember.create | bitable_v1_appRoleMember_create | lark-mcp__bitable_v1_appRoleMember_create |
| bitable.v1.appRoleMember.delete | bitable_v1_appRoleMember_delete | lark-mcp__bitable_v1_appRoleMember_delete |
| bitable.v1.appRoleMember.list | bitable_v1_appRoleMember_list | lark-mcp__bitable_v1_appRoleMember_list |
| bitable.v1.appTable.batchCreate | bitable_v1_appTable_batchCreate | lark-mcp__bitable_v1_appTable_batchCreate |
| bitable.v1.appTable.batchDelete | bitable_v1_appTable_batchDelete | lark-mcp__bitable_v1_appTable_batchDelete |
| bitable.v1.appTable.create | bitable_v1_appTable_create | lark-mcp__bitable_v1_appTable_create |
| bitable.v1.appTable.delete | bitable_v1_appTable_delete | lark-mcp__bitable_v1_appTable_delete |
| bitable.v1.appTable.list | bitable_v1_appTable_list | lark-mcp__bitable_v1_appTable_list |
| bitable.v1.appTable.patch | bitable_v1_appTable_patch | lark-mcp__bitable_v1_appTable_patch |
| bitable.v1.appTableField.create | bitable_v1_appTableField_create | lark-mcp__bitable_v1_appTableField_create |
| bitable.v1.appTableField.delete | bitable_v1_appTableField_delete | lark-mcp__bitable_v1_appTableField_delete |
| bitable.v1.appTableField.list | bitable_v1_appTableField_list | lark-mcp__bitable_v1_appTableField_list |
| bitable.v1.appTableField.update | bitable_v1_appTableField_update | lark-mcp__bitable_v1_appTableField_update |
| bitable.v1.appTableForm.get | bitable_v1_appTableForm_get | lark-mcp__bitable_v1_appTableForm_get |
| bitable.v1.appTableForm.patch | bitable_v1_appTableForm_patch | lark-mcp__bitable_v1_appTableForm_patch |
| bitable.v1.appTableFormField.list | bitable_v1_appTableFormField_list | lark-mcp__bitable_v1_appTableFormField_list |
| bitable.v1.appTableFormField.patch | bitable_v1_appTableFormField_patch | lark-mcp__bitable_v1_appTableFormField_patch |
| bitable.v1.appTableRecord.batchCreate | bitable_v1_appTableRecord_batchCreate | lark-mcp__bitable_v1_appTableRecord_batchCreate |
| bitable.v1.appTableRecord.batchDelete | bitable_v1_appTableRecord_batchDelete | lark-mcp__bitable_v1_appTableRecord_batchDelete |
| bitable.v1.appTableRecord.batchGet | bitable_v1_appTableRecord_batchGet | lark-mcp__bitable_v1_appTableRecord_batchGet |
| bitable.v1.appTableRecord.batchUpdate | bitable_v1_appTableRecord_batchUpdate | lark-mcp__bitable_v1_appTableRecord_batchUpdate |
| bitable.v1.appTableRecord.create | bitable_v1_appTableRecord_create | lark-mcp__bitable_v1_appTableRecord_create |
| bitable.v1.appTableRecord.delete | bitable_v1_appTableRecord_delete | lark-mcp__bitable_v1_appTableRecord_delete |
| bitable.v1.appTableRecord.get | bitable_v1_appTableRecord_get | lark-mcp__bitable_v1_appTableRecord_get |
| bitable.v1.appTableRecord.list | bitable_v1_appTableRecord_list | lark-mcp__bitable_v1_appTableRecord_list |
| bitable.v1.appTableRecord.search | bitable_v1_appTableRecord_search | lark-mcp__bitable_v1_appTableRecord_search |
| bitable.v1.appTableRecord.update | bitable_v1_appTableRecord_update | lark-mcp__bitable_v1_appTableRecord_update |
| bitable.v1.appTableView.create | bitable_v1_appTableView_create | lark-mcp__bitable_v1_appTableView_create |
| bitable.v1.appTableView.delete | bitable_v1_appTableView_delete | lark-mcp__bitable_v1_appTableView_delete |
| bitable.v1.appTableView.get | bitable_v1_appTableView_get | lark-mcp__bitable_v1_appTableView_get |
| bitable.v1.appTableView.list | bitable_v1_appTableView_list | lark-mcp__bitable_v1_appTableView_list |
| bitable.v1.appTableView.patch | bitable_v1_appTableView_patch | lark-mcp__bitable_v1_appTableView_patch |
| bitable.v1.appWorkflow.list | bitable_v1_appWorkflow_list | lark-mcp__bitable_v1_appWorkflow_list |
| bitable.v1.appWorkflow.update | bitable_v1_appWorkflow_update | lark-mcp__bitable_v1_appWorkflow_update |

## boardV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| board.v1.whiteboardNode.list | board_v1_whiteboardNode_list | lark-mcp__board_v1_whiteboardNode_list |

## calendarV4 (41)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| calendar.v4.calendar.create | calendar_v4_calendar_create | lark-mcp__calendar_v4_calendar_create |
| calendar.v4.calendar.delete | calendar_v4_calendar_delete | lark-mcp__calendar_v4_calendar_delete |
| calendar.v4.calendar.get | calendar_v4_calendar_get | lark-mcp__calendar_v4_calendar_get |
| calendar.v4.calendar.list | calendar_v4_calendar_list | lark-mcp__calendar_v4_calendar_list |
| calendar.v4.calendar.patch | calendar_v4_calendar_patch | lark-mcp__calendar_v4_calendar_patch |
| calendar.v4.calendar.primary | calendar_v4_calendar_primary | lark-mcp__calendar_v4_calendar_primary |
| calendar.v4.calendar.search | calendar_v4_calendar_search | lark-mcp__calendar_v4_calendar_search |
| calendar.v4.calendar.subscribe | calendar_v4_calendar_subscribe | lark-mcp__calendar_v4_calendar_subscribe |
| calendar.v4.calendar.subscription | calendar_v4_calendar_subscription | lark-mcp__calendar_v4_calendar_subscription |
| calendar.v4.calendar.unsubscribe | calendar_v4_calendar_unsubscribe | lark-mcp__calendar_v4_calendar_unsubscribe |
| calendar.v4.calendar.unsubscription | calendar_v4_calendar_unsubscription | lark-mcp__calendar_v4_calendar_unsubscription |
| calendar.v4.calendarAcl.create | calendar_v4_calendarAcl_create | lark-mcp__calendar_v4_calendarAcl_create |
| calendar.v4.calendarAcl.delete | calendar_v4_calendarAcl_delete | lark-mcp__calendar_v4_calendarAcl_delete |
| calendar.v4.calendarAcl.list | calendar_v4_calendarAcl_list | lark-mcp__calendar_v4_calendarAcl_list |
| calendar.v4.calendarAcl.subscription | calendar_v4_calendarAcl_subscription | lark-mcp__calendar_v4_calendarAcl_subscription |
| calendar.v4.calendarAcl.unsubscription | calendar_v4_calendarAcl_unsubscription | lark-mcp__calendar_v4_calendarAcl_unsubscription |
| calendar.v4.calendarEvent.create | calendar_v4_calendarEvent_create | lark-mcp__calendar_v4_calendarEvent_create |
| calendar.v4.calendarEvent.delete | calendar_v4_calendarEvent_delete | lark-mcp__calendar_v4_calendarEvent_delete |
| calendar.v4.calendarEvent.get | calendar_v4_calendarEvent_get | lark-mcp__calendar_v4_calendarEvent_get |
| calendar.v4.calendarEvent.instances | calendar_v4_calendarEvent_instances | lark-mcp__calendar_v4_calendarEvent_instances |
| calendar.v4.calendarEvent.instanceView | calendar_v4_calendarEvent_instanceView | lark-mcp__calendar_v4_calendarEvent_instanceView |
| calendar.v4.calendarEvent.list | calendar_v4_calendarEvent_list | lark-mcp__calendar_v4_calendarEvent_list |
| calendar.v4.calendarEvent.patch | calendar_v4_calendarEvent_patch | lark-mcp__calendar_v4_calendarEvent_patch |
| calendar.v4.calendarEvent.reply | calendar_v4_calendarEvent_reply | lark-mcp__calendar_v4_calendarEvent_reply |
| calendar.v4.calendarEvent.search | calendar_v4_calendarEvent_search | lark-mcp__calendar_v4_calendarEvent_search |
| calendar.v4.calendarEvent.subscription | calendar_v4_calendarEvent_subscription | lark-mcp__calendar_v4_calendarEvent_subscription |
| calendar.v4.calendarEvent.unsubscription | calendar_v4_calendarEvent_unsubscription | lark-mcp__calendar_v4_calendarEvent_unsubscription |
| calendar.v4.calendarEventAttendee.batchDelete | calendar_v4_calendarEventAttendee_batchDelete | lark-mcp__calendar_v4_calendarEventAttendee_batchDelete |
| calendar.v4.calendarEventAttendee.create | calendar_v4_calendarEventAttendee_create | lark-mcp__calendar_v4_calendarEventAttendee_create |
| calendar.v4.calendarEventAttendee.list | calendar_v4_calendarEventAttendee_list | lark-mcp__calendar_v4_calendarEventAttendee_list |
| calendar.v4.calendarEventAttendeeChatMember.list | calendar_v4_calendarEventAttendeeChatMember_list | lark-mcp__calendar_v4_calendarEventAttendeeChatMember_list |
| calendar.v4.calendarEventMeetingChat.create | calendar_v4_calendarEventMeetingChat_create | lark-mcp__calendar_v4_calendarEventMeetingChat_create |
| calendar.v4.calendarEventMeetingChat.delete | calendar_v4_calendarEventMeetingChat_delete | lark-mcp__calendar_v4_calendarEventMeetingChat_delete |
| calendar.v4.calendarEventMeetingMinute.create | calendar_v4_calendarEventMeetingMinute_create | lark-mcp__calendar_v4_calendarEventMeetingMinute_create |
| calendar.v4.exchangeBinding.create | calendar_v4_exchangeBinding_create | lark-mcp__calendar_v4_exchangeBinding_create |
| calendar.v4.exchangeBinding.delete | calendar_v4_exchangeBinding_delete | lark-mcp__calendar_v4_exchangeBinding_delete |
| calendar.v4.exchangeBinding.get | calendar_v4_exchangeBinding_get | lark-mcp__calendar_v4_exchangeBinding_get |
| calendar.v4.freebusy.list | calendar_v4_freebusy_list | lark-mcp__calendar_v4_freebusy_list |
| calendar.v4.setting.generateCaldavConf | calendar_v4_setting_generateCaldavConf | lark-mcp__calendar_v4_setting_generateCaldavConf |
| calendar.v4.timeoffEvent.create | calendar_v4_timeoffEvent_create | lark-mcp__calendar_v4_timeoffEvent_create |
| calendar.v4.timeoffEvent.delete | calendar_v4_timeoffEvent_delete | lark-mcp__calendar_v4_timeoffEvent_delete |

## cardkitV1 (10)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| cardkit.v1.card.batchUpdate | cardkit_v1_card_batchUpdate | lark-mcp__cardkit_v1_card_batchUpdate |
| cardkit.v1.card.create | cardkit_v1_card_create | lark-mcp__cardkit_v1_card_create |
| cardkit.v1.card.idConvert | cardkit_v1_card_idConvert | lark-mcp__cardkit_v1_card_idConvert |
| cardkit.v1.card.settings | cardkit_v1_card_settings | lark-mcp__cardkit_v1_card_settings |
| cardkit.v1.card.update | cardkit_v1_card_update | lark-mcp__cardkit_v1_card_update |
| cardkit.v1.cardElement.content | cardkit_v1_cardElement_content | lark-mcp__cardkit_v1_cardElement_content |
| cardkit.v1.cardElement.create | cardkit_v1_cardElement_create | lark-mcp__cardkit_v1_cardElement_create |
| cardkit.v1.cardElement.delete | cardkit_v1_cardElement_delete | lark-mcp__cardkit_v1_cardElement_delete |
| cardkit.v1.cardElement.patch | cardkit_v1_cardElement_patch | lark-mcp__cardkit_v1_cardElement_patch |
| cardkit.v1.cardElement.update | cardkit_v1_cardElement_update | lark-mcp__cardkit_v1_cardElement_update |

## compensationV1 (6)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| compensation.v1.archive.query | compensation_v1_archive_query | lark-mcp__compensation_v1_archive_query |
| compensation.v1.changeReason.list | compensation_v1_changeReason_list | lark-mcp__compensation_v1_changeReason_list |
| compensation.v1.indicator.list | compensation_v1_indicator_list | lark-mcp__compensation_v1_indicator_list |
| compensation.v1.item.list | compensation_v1_item_list | lark-mcp__compensation_v1_item_list |
| compensation.v1.itemCategory.list | compensation_v1_itemCategory_list | lark-mcp__compensation_v1_itemCategory_list |
| compensation.v1.plan.list | compensation_v1_plan_list | lark-mcp__compensation_v1_plan_list |

## contactV3 (70)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| contact.v3.customAttr.list | contact_v3_customAttr_list | lark-mcp__contact_v3_customAttr_list |
| contact.v3.department.batch | contact_v3_department_batch | lark-mcp__contact_v3_department_batch |
| contact.v3.department.children | contact_v3_department_children | lark-mcp__contact_v3_department_children |
| contact.v3.department.create | contact_v3_department_create | lark-mcp__contact_v3_department_create |
| contact.v3.department.delete | contact_v3_department_delete | lark-mcp__contact_v3_department_delete |
| contact.v3.department.get | contact_v3_department_get | lark-mcp__contact_v3_department_get |
| contact.v3.department.list | contact_v3_department_list | lark-mcp__contact_v3_department_list |
| contact.v3.department.parent | contact_v3_department_parent | lark-mcp__contact_v3_department_parent |
| contact.v3.department.patch | contact_v3_department_patch | lark-mcp__contact_v3_department_patch |
| contact.v3.department.search | contact_v3_department_search | lark-mcp__contact_v3_department_search |
| contact.v3.department.unbindDepartmentChat | contact_v3_department_unbindDepartmentChat | lark-mcp__contact_v3_department_unbindDepartmentChat |
| contact.v3.department.update | contact_v3_department_update | lark-mcp__contact_v3_department_update |
| contact.v3.department.updateDepartmentId | contact_v3_department_updateDepartmentId | lark-mcp__contact_v3_department_updateDepartmentId |
| contact.v3.employeeTypeEnum.create | contact_v3_employeeTypeEnum_create | lark-mcp__contact_v3_employeeTypeEnum_create |
| contact.v3.employeeTypeEnum.delete | contact_v3_employeeTypeEnum_delete | lark-mcp__contact_v3_employeeTypeEnum_delete |
| contact.v3.employeeTypeEnum.list | contact_v3_employeeTypeEnum_list | lark-mcp__contact_v3_employeeTypeEnum_list |
| contact.v3.employeeTypeEnum.update | contact_v3_employeeTypeEnum_update | lark-mcp__contact_v3_employeeTypeEnum_update |
| contact.v3.functionalRole.create | contact_v3_functionalRole_create | lark-mcp__contact_v3_functionalRole_create |
| contact.v3.functionalRole.delete | contact_v3_functionalRole_delete | lark-mcp__contact_v3_functionalRole_delete |
| contact.v3.functionalRole.update | contact_v3_functionalRole_update | lark-mcp__contact_v3_functionalRole_update |
| contact.v3.functionalRoleMember.batchCreate | contact_v3_functionalRoleMember_batchCreate | lark-mcp__contact_v3_functionalRoleMember_batchCreate |
| contact.v3.functionalRoleMember.batchDelete | contact_v3_functionalRoleMember_batchDelete | lark-mcp__contact_v3_functionalRoleMember_batchDelete |
| contact.v3.functionalRoleMember.get | contact_v3_functionalRoleMember_get | lark-mcp__contact_v3_functionalRoleMember_get |
| contact.v3.functionalRoleMember.list | contact_v3_functionalRoleMember_list | lark-mcp__contact_v3_functionalRoleMember_list |
| contact.v3.functionalRoleMember.scopes | contact_v3_functionalRoleMember_scopes | lark-mcp__contact_v3_functionalRoleMember_scopes |
| contact.v3.group.create | contact_v3_group_create | lark-mcp__contact_v3_group_create |
| contact.v3.group.delete | contact_v3_group_delete | lark-mcp__contact_v3_group_delete |
| contact.v3.group.get | contact_v3_group_get | lark-mcp__contact_v3_group_get |
| contact.v3.group.memberBelong | contact_v3_group_memberBelong | lark-mcp__contact_v3_group_memberBelong |
| contact.v3.group.patch | contact_v3_group_patch | lark-mcp__contact_v3_group_patch |
| contact.v3.group.simplelist | contact_v3_group_simplelist | lark-mcp__contact_v3_group_simplelist |
| contact.v3.groupMember.add | contact_v3_groupMember_add | lark-mcp__contact_v3_groupMember_add |
| contact.v3.groupMember.batchAdd | contact_v3_groupMember_batchAdd | lark-mcp__contact_v3_groupMember_batchAdd |
| contact.v3.groupMember.batchRemove | contact_v3_groupMember_batchRemove | lark-mcp__contact_v3_groupMember_batchRemove |
| contact.v3.groupMember.remove | contact_v3_groupMember_remove | lark-mcp__contact_v3_groupMember_remove |
| contact.v3.groupMember.simplelist | contact_v3_groupMember_simplelist | lark-mcp__contact_v3_groupMember_simplelist |
| contact.v3.jobFamily.create | contact_v3_jobFamily_create | lark-mcp__contact_v3_jobFamily_create |
| contact.v3.jobFamily.delete | contact_v3_jobFamily_delete | lark-mcp__contact_v3_jobFamily_delete |
| contact.v3.jobFamily.get | contact_v3_jobFamily_get | lark-mcp__contact_v3_jobFamily_get |
| contact.v3.jobFamily.list | contact_v3_jobFamily_list | lark-mcp__contact_v3_jobFamily_list |
| contact.v3.jobFamily.update | contact_v3_jobFamily_update | lark-mcp__contact_v3_jobFamily_update |
| contact.v3.jobLevel.create | contact_v3_jobLevel_create | lark-mcp__contact_v3_jobLevel_create |
| contact.v3.jobLevel.delete | contact_v3_jobLevel_delete | lark-mcp__contact_v3_jobLevel_delete |
| contact.v3.jobLevel.get | contact_v3_jobLevel_get | lark-mcp__contact_v3_jobLevel_get |
| contact.v3.jobLevel.list | contact_v3_jobLevel_list | lark-mcp__contact_v3_jobLevel_list |
| contact.v3.jobLevel.update | contact_v3_jobLevel_update | lark-mcp__contact_v3_jobLevel_update |
| contact.v3.jobTitle.get | contact_v3_jobTitle_get | lark-mcp__contact_v3_jobTitle_get |
| contact.v3.jobTitle.list | contact_v3_jobTitle_list | lark-mcp__contact_v3_jobTitle_list |
| contact.v3.scope.list | contact_v3_scope_list | lark-mcp__contact_v3_scope_list |
| contact.v3.unit.bindDepartment | contact_v3_unit_bindDepartment | lark-mcp__contact_v3_unit_bindDepartment |
| contact.v3.unit.create | contact_v3_unit_create | lark-mcp__contact_v3_unit_create |
| contact.v3.unit.delete | contact_v3_unit_delete | lark-mcp__contact_v3_unit_delete |
| contact.v3.unit.get | contact_v3_unit_get | lark-mcp__contact_v3_unit_get |
| contact.v3.unit.list | contact_v3_unit_list | lark-mcp__contact_v3_unit_list |
| contact.v3.unit.listDepartment | contact_v3_unit_listDepartment | lark-mcp__contact_v3_unit_listDepartment |
| contact.v3.unit.patch | contact_v3_unit_patch | lark-mcp__contact_v3_unit_patch |
| contact.v3.unit.unbindDepartment | contact_v3_unit_unbindDepartment | lark-mcp__contact_v3_unit_unbindDepartment |
| contact.v3.user.batch | contact_v3_user_batch | lark-mcp__contact_v3_user_batch |
| contact.v3.user.batchGetId | contact_v3_user_batchGetId | lark-mcp__contact_v3_user_batchGetId |
| contact.v3.user.create | contact_v3_user_create | lark-mcp__contact_v3_user_create |
| contact.v3.user.delete | contact_v3_user_delete | lark-mcp__contact_v3_user_delete |
| contact.v3.user.findByDepartment | contact_v3_user_findByDepartment | lark-mcp__contact_v3_user_findByDepartment |
| contact.v3.user.get | contact_v3_user_get | lark-mcp__contact_v3_user_get |
| contact.v3.user.list | contact_v3_user_list | lark-mcp__contact_v3_user_list |
| contact.v3.user.patch | contact_v3_user_patch | lark-mcp__contact_v3_user_patch |
| contact.v3.user.resurrect | contact_v3_user_resurrect | lark-mcp__contact_v3_user_resurrect |
| contact.v3.user.update | contact_v3_user_update | lark-mcp__contact_v3_user_update |
| contact.v3.user.updateUserId | contact_v3_user_updateUserId | lark-mcp__contact_v3_user_updateUserId |
| contact.v3.workCity.get | contact_v3_workCity_get | lark-mcp__contact_v3_workCity_get |
| contact.v3.workCity.list | contact_v3_workCity_list | lark-mcp__contact_v3_workCity_list |

## corehrV1 (103)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| corehr.v1.assignedUser.search | corehr_v1_assignedUser_search | lark-mcp__corehr_v1_assignedUser_search |
| corehr.v1.authorization.addRoleAssign | corehr_v1_authorization_addRoleAssign | lark-mcp__corehr_v1_authorization_addRoleAssign |
| corehr.v1.authorization.getByParam | corehr_v1_authorization_getByParam | lark-mcp__corehr_v1_authorization_getByParam |
| corehr.v1.authorization.query | corehr_v1_authorization_query | lark-mcp__corehr_v1_authorization_query |
| corehr.v1.authorization.removeRoleAssign | corehr_v1_authorization_removeRoleAssign | lark-mcp__corehr_v1_authorization_removeRoleAssign |
| corehr.v1.authorization.updateRoleAssign | corehr_v1_authorization_updateRoleAssign | lark-mcp__corehr_v1_authorization_updateRoleAssign |
| corehr.v1.commonDataId.convert | corehr_v1_commonDataId_convert | lark-mcp__corehr_v1_commonDataId_convert |
| corehr.v1.commonDataMetaData.addEnumOption | corehr_v1_commonDataMetaData_addEnumOption | lark-mcp__corehr_v1_commonDataMetaData_addEnumOption |
| corehr.v1.commonDataMetaData.editEnumOption | corehr_v1_commonDataMetaData_editEnumOption | lark-mcp__corehr_v1_commonDataMetaData_editEnumOption |
| corehr.v1.company.create | corehr_v1_company_create | lark-mcp__corehr_v1_company_create |
| corehr.v1.company.delete | corehr_v1_company_delete | lark-mcp__corehr_v1_company_delete |
| corehr.v1.company.get | corehr_v1_company_get | lark-mcp__corehr_v1_company_get |
| corehr.v1.company.list | corehr_v1_company_list | lark-mcp__corehr_v1_company_list |
| corehr.v1.company.patch | corehr_v1_company_patch | lark-mcp__corehr_v1_company_patch |
| corehr.v1.compensationStandard.match | corehr_v1_compensationStandard_match | lark-mcp__corehr_v1_compensationStandard_match |
| corehr.v1.contract.create | corehr_v1_contract_create | lark-mcp__corehr_v1_contract_create |
| corehr.v1.contract.delete | corehr_v1_contract_delete | lark-mcp__corehr_v1_contract_delete |
| corehr.v1.contract.get | corehr_v1_contract_get | lark-mcp__corehr_v1_contract_get |
| corehr.v1.contract.list | corehr_v1_contract_list | lark-mcp__corehr_v1_contract_list |
| corehr.v1.contract.patch | corehr_v1_contract_patch | lark-mcp__corehr_v1_contract_patch |
| corehr.v1.countryRegion.get | corehr_v1_countryRegion_get | lark-mcp__corehr_v1_countryRegion_get |
| corehr.v1.countryRegion.list | corehr_v1_countryRegion_list | lark-mcp__corehr_v1_countryRegion_list |
| corehr.v1.currency.get | corehr_v1_currency_get | lark-mcp__corehr_v1_currency_get |
| corehr.v1.currency.list | corehr_v1_currency_list | lark-mcp__corehr_v1_currency_list |
| corehr.v1.customField.getByParam | corehr_v1_customField_getByParam | lark-mcp__corehr_v1_customField_getByParam |
| corehr.v1.customField.listObjectApiName | corehr_v1_customField_listObjectApiName | lark-mcp__corehr_v1_customField_listObjectApiName |
| corehr.v1.customField.query | corehr_v1_customField_query | lark-mcp__corehr_v1_customField_query |
| corehr.v1.department.create | corehr_v1_department_create | lark-mcp__corehr_v1_department_create |
| corehr.v1.department.delete | corehr_v1_department_delete | lark-mcp__corehr_v1_department_delete |
| corehr.v1.department.get | corehr_v1_department_get | lark-mcp__corehr_v1_department_get |
| corehr.v1.department.list | corehr_v1_department_list | lark-mcp__corehr_v1_department_list |
| corehr.v1.department.patch | corehr_v1_department_patch | lark-mcp__corehr_v1_department_patch |
| corehr.v1.employeeType.create | corehr_v1_employeeType_create | lark-mcp__corehr_v1_employeeType_create |
| corehr.v1.employeeType.delete | corehr_v1_employeeType_delete | lark-mcp__corehr_v1_employeeType_delete |
| corehr.v1.employeeType.get | corehr_v1_employeeType_get | lark-mcp__corehr_v1_employeeType_get |
| corehr.v1.employeeType.list | corehr_v1_employeeType_list | lark-mcp__corehr_v1_employeeType_list |
| corehr.v1.employeeType.patch | corehr_v1_employeeType_patch | lark-mcp__corehr_v1_employeeType_patch |
| corehr.v1.employment.create | corehr_v1_employment_create | lark-mcp__corehr_v1_employment_create |
| corehr.v1.employment.delete | corehr_v1_employment_delete | lark-mcp__corehr_v1_employment_delete |
| corehr.v1.employment.patch | corehr_v1_employment_patch | lark-mcp__corehr_v1_employment_patch |
| corehr.v1.job.create | corehr_v1_job_create | lark-mcp__corehr_v1_job_create |
| corehr.v1.job.delete | corehr_v1_job_delete | lark-mcp__corehr_v1_job_delete |
| corehr.v1.job.get | corehr_v1_job_get | lark-mcp__corehr_v1_job_get |
| corehr.v1.job.list | corehr_v1_job_list | lark-mcp__corehr_v1_job_list |
| corehr.v1.job.patch | corehr_v1_job_patch | lark-mcp__corehr_v1_job_patch |
| corehr.v1.jobChange.create | corehr_v1_jobChange_create | lark-mcp__corehr_v1_jobChange_create |
| corehr.v1.jobData.create | corehr_v1_jobData_create | lark-mcp__corehr_v1_jobData_create |
| corehr.v1.jobData.delete | corehr_v1_jobData_delete | lark-mcp__corehr_v1_jobData_delete |
| corehr.v1.jobData.get | corehr_v1_jobData_get | lark-mcp__corehr_v1_jobData_get |
| corehr.v1.jobData.list | corehr_v1_jobData_list | lark-mcp__corehr_v1_jobData_list |
| corehr.v1.jobData.patch | corehr_v1_jobData_patch | lark-mcp__corehr_v1_jobData_patch |
| corehr.v1.jobFamily.create | corehr_v1_jobFamily_create | lark-mcp__corehr_v1_jobFamily_create |
| corehr.v1.jobFamily.delete | corehr_v1_jobFamily_delete | lark-mcp__corehr_v1_jobFamily_delete |
| corehr.v1.jobFamily.get | corehr_v1_jobFamily_get | lark-mcp__corehr_v1_jobFamily_get |
| corehr.v1.jobFamily.list | corehr_v1_jobFamily_list | lark-mcp__corehr_v1_jobFamily_list |
| corehr.v1.jobFamily.patch | corehr_v1_jobFamily_patch | lark-mcp__corehr_v1_jobFamily_patch |
| corehr.v1.jobLevel.create | corehr_v1_jobLevel_create | lark-mcp__corehr_v1_jobLevel_create |
| corehr.v1.jobLevel.delete | corehr_v1_jobLevel_delete | lark-mcp__corehr_v1_jobLevel_delete |
| corehr.v1.jobLevel.get | corehr_v1_jobLevel_get | lark-mcp__corehr_v1_jobLevel_get |
| corehr.v1.jobLevel.list | corehr_v1_jobLevel_list | lark-mcp__corehr_v1_jobLevel_list |
| corehr.v1.jobLevel.patch | corehr_v1_jobLevel_patch | lark-mcp__corehr_v1_jobLevel_patch |
| corehr.v1.leave.calendarByScope | corehr_v1_leave_calendarByScope | lark-mcp__corehr_v1_leave_calendarByScope |
| corehr.v1.leave.leaveBalances | corehr_v1_leave_leaveBalances | lark-mcp__corehr_v1_leave_leaveBalances |
| corehr.v1.leave.leaveRequestHistory | corehr_v1_leave_leaveRequestHistory | lark-mcp__corehr_v1_leave_leaveRequestHistory |
| corehr.v1.leave.leaveTypes | corehr_v1_leave_leaveTypes | lark-mcp__corehr_v1_leave_leaveTypes |
| corehr.v1.leave.workCalendar | corehr_v1_leave_workCalendar | lark-mcp__corehr_v1_leave_workCalendar |
| corehr.v1.leave.workCalendarDate | corehr_v1_leave_workCalendarDate | lark-mcp__corehr_v1_leave_workCalendarDate |
| corehr.v1.leaveGrantingRecord.create | corehr_v1_leaveGrantingRecord_create | lark-mcp__corehr_v1_leaveGrantingRecord_create |
| corehr.v1.leaveGrantingRecord.delete | corehr_v1_leaveGrantingRecord_delete | lark-mcp__corehr_v1_leaveGrantingRecord_delete |
| corehr.v1.location.create | corehr_v1_location_create | lark-mcp__corehr_v1_location_create |
| corehr.v1.location.delete | corehr_v1_location_delete | lark-mcp__corehr_v1_location_delete |
| corehr.v1.location.get | corehr_v1_location_get | lark-mcp__corehr_v1_location_get |
| corehr.v1.location.list | corehr_v1_location_list | lark-mcp__corehr_v1_location_list |
| corehr.v1.nationalIdType.create | corehr_v1_nationalIdType_create | lark-mcp__corehr_v1_nationalIdType_create |
| corehr.v1.nationalIdType.delete | corehr_v1_nationalIdType_delete | lark-mcp__corehr_v1_nationalIdType_delete |
| corehr.v1.nationalIdType.get | corehr_v1_nationalIdType_get | lark-mcp__corehr_v1_nationalIdType_get |
| corehr.v1.nationalIdType.list | corehr_v1_nationalIdType_list | lark-mcp__corehr_v1_nationalIdType_list |
| corehr.v1.nationalIdType.patch | corehr_v1_nationalIdType_patch | lark-mcp__corehr_v1_nationalIdType_patch |
| corehr.v1.offboarding.query | corehr_v1_offboarding_query | lark-mcp__corehr_v1_offboarding_query |
| corehr.v1.offboarding.search | corehr_v1_offboarding_search | lark-mcp__corehr_v1_offboarding_search |
| corehr.v1.offboarding.submit | corehr_v1_offboarding_submit | lark-mcp__corehr_v1_offboarding_submit |
| corehr.v1.person.create | corehr_v1_person_create | lark-mcp__corehr_v1_person_create |
| corehr.v1.person.delete | corehr_v1_person_delete | lark-mcp__corehr_v1_person_delete |
| corehr.v1.person.get | corehr_v1_person_get | lark-mcp__corehr_v1_person_get |
| corehr.v1.person.patch | corehr_v1_person_patch | lark-mcp__corehr_v1_person_patch |
| corehr.v1.preHire.delete | corehr_v1_preHire_delete | lark-mcp__corehr_v1_preHire_delete |
| corehr.v1.preHire.get | corehr_v1_preHire_get | lark-mcp__corehr_v1_preHire_get |
| corehr.v1.preHire.list | corehr_v1_preHire_list | lark-mcp__corehr_v1_preHire_list |
| corehr.v1.preHire.patch | corehr_v1_preHire_patch | lark-mcp__corehr_v1_preHire_patch |
| corehr.v1.processFormVariableData.get | corehr_v1_processFormVariableData_get | lark-mcp__corehr_v1_processFormVariableData_get |
| corehr.v1.securityGroup.list | corehr_v1_securityGroup_list | lark-mcp__corehr_v1_securityGroup_list |
| corehr.v1.securityGroup.query | corehr_v1_securityGroup_query | lark-mcp__corehr_v1_securityGroup_query |
| corehr.v1.subdivision.get | corehr_v1_subdivision_get | lark-mcp__corehr_v1_subdivision_get |
| corehr.v1.subdivision.list | corehr_v1_subdivision_list | lark-mcp__corehr_v1_subdivision_list |
| corehr.v1.subregion.get | corehr_v1_subregion_get | lark-mcp__corehr_v1_subregion_get |
| corehr.v1.subregion.list | corehr_v1_subregion_list | lark-mcp__corehr_v1_subregion_list |
| corehr.v1.transferReason.query | corehr_v1_transferReason_query | lark-mcp__corehr_v1_transferReason_query |
| corehr.v1.transferType.query | corehr_v1_transferType_query | lark-mcp__corehr_v1_transferType_query |
| corehr.v1.workingHoursType.create | corehr_v1_workingHoursType_create | lark-mcp__corehr_v1_workingHoursType_create |
| corehr.v1.workingHoursType.delete | corehr_v1_workingHoursType_delete | lark-mcp__corehr_v1_workingHoursType_delete |
| corehr.v1.workingHoursType.get | corehr_v1_workingHoursType_get | lark-mcp__corehr_v1_workingHoursType_get |
| corehr.v1.workingHoursType.list | corehr_v1_workingHoursType_list | lark-mcp__corehr_v1_workingHoursType_list |
| corehr.v1.workingHoursType.patch | corehr_v1_workingHoursType_patch | lark-mcp__corehr_v1_workingHoursType_patch |

## corehrV2 (115)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| corehr.v2.approvalGroups.get | corehr_v2_approvalGroups_get | lark-mcp__corehr_v2_approvalGroups_get |
| corehr.v2.approvalGroups.openQueryDepartmentChangeListByIds | corehr_v2_approvalGroups_openQueryDepartmentChangeListByIds | lark-mcp__corehr_v2_approvalGroups_openQueryDepartmentChangeListByIds |
| corehr.v2.approvalGroups.openQueryJobChangeListByIds | corehr_v2_approvalGroups_openQueryJobChangeListByIds | lark-mcp__corehr_v2_approvalGroups_openQueryJobChangeListByIds |
| corehr.v2.approver.list | corehr_v2_approver_list | lark-mcp__corehr_v2_approver_list |
| corehr.v2.basicInfoBank.search | corehr_v2_basicInfoBank_search | lark-mcp__corehr_v2_basicInfoBank_search |
| corehr.v2.basicInfoBankBranch.search | corehr_v2_basicInfoBankBranch_search | lark-mcp__corehr_v2_basicInfoBankBranch_search |
| corehr.v2.basicInfoCity.search | corehr_v2_basicInfoCity_search | lark-mcp__corehr_v2_basicInfoCity_search |
| corehr.v2.basicInfoCountryRegion.search | corehr_v2_basicInfoCountryRegion_search | lark-mcp__corehr_v2_basicInfoCountryRegion_search |
| corehr.v2.basicInfoCountryRegionSubdivision.search | corehr_v2_basicInfoCountryRegionSubdivision_search | lark-mcp__corehr_v2_basicInfoCountryRegionSubdivision_search |
| corehr.v2.basicInfoCurrency.search | corehr_v2_basicInfoCurrency_search | lark-mcp__corehr_v2_basicInfoCurrency_search |
| corehr.v2.basicInfoDistrict.search | corehr_v2_basicInfoDistrict_search | lark-mcp__corehr_v2_basicInfoDistrict_search |
| corehr.v2.basicInfoLanguage.search | corehr_v2_basicInfoLanguage_search | lark-mcp__corehr_v2_basicInfoLanguage_search |
| corehr.v2.basicInfoNationality.search | corehr_v2_basicInfoNationality_search | lark-mcp__corehr_v2_basicInfoNationality_search |
| corehr.v2.basicInfoTimeZone.search | corehr_v2_basicInfoTimeZone_search | lark-mcp__corehr_v2_basicInfoTimeZone_search |
| corehr.v2.bp.getByDepartment | corehr_v2_bp_getByDepartment | lark-mcp__corehr_v2_bp_getByDepartment |
| corehr.v2.bp.list | corehr_v2_bp_list | lark-mcp__corehr_v2_bp_list |
| corehr.v2.company.active | corehr_v2_company_active | lark-mcp__corehr_v2_company_active |
| corehr.v2.company.batchGet | corehr_v2_company_batchGet | lark-mcp__corehr_v2_company_batchGet |
| corehr.v2.company.queryRecentChange | corehr_v2_company_queryRecentChange | lark-mcp__corehr_v2_company_queryRecentChange |
| corehr.v2.contract.search | corehr_v2_contract_search | lark-mcp__corehr_v2_contract_search |
| corehr.v2.costAllocation.batchQuery | corehr_v2_costAllocation_batchQuery | lark-mcp__corehr_v2_costAllocation_batchQuery |
| corehr.v2.costAllocation.createVersion | corehr_v2_costAllocation_createVersion | lark-mcp__corehr_v2_costAllocation_createVersion |
| corehr.v2.costAllocation.removeVersion | corehr_v2_costAllocation_removeVersion | lark-mcp__corehr_v2_costAllocation_removeVersion |
| corehr.v2.costAllocation.updateVersion | corehr_v2_costAllocation_updateVersion | lark-mcp__corehr_v2_costAllocation_updateVersion |
| corehr.v2.costCenter.create | corehr_v2_costCenter_create | lark-mcp__corehr_v2_costCenter_create |
| corehr.v2.costCenter.delete | corehr_v2_costCenter_delete | lark-mcp__corehr_v2_costCenter_delete |
| corehr.v2.costCenter.patch | corehr_v2_costCenter_patch | lark-mcp__corehr_v2_costCenter_patch |
| corehr.v2.costCenter.queryRecentChange | corehr_v2_costCenter_queryRecentChange | lark-mcp__corehr_v2_costCenter_queryRecentChange |
| corehr.v2.costCenter.search | corehr_v2_costCenter_search | lark-mcp__corehr_v2_costCenter_search |
| corehr.v2.costCenterVersion.create | corehr_v2_costCenterVersion_create | lark-mcp__corehr_v2_costCenterVersion_create |
| corehr.v2.costCenterVersion.delete | corehr_v2_costCenterVersion_delete | lark-mcp__corehr_v2_costCenterVersion_delete |
| corehr.v2.costCenterVersion.patch | corehr_v2_costCenterVersion_patch | lark-mcp__corehr_v2_costCenterVersion_patch |
| corehr.v2.customOrg.active | corehr_v2_customOrg_active | lark-mcp__corehr_v2_customOrg_active |
| corehr.v2.customOrg.create | corehr_v2_customOrg_create | lark-mcp__corehr_v2_customOrg_create |
| corehr.v2.customOrg.deleteOrg | corehr_v2_customOrg_deleteOrg | lark-mcp__corehr_v2_customOrg_deleteOrg |
| corehr.v2.customOrg.patch | corehr_v2_customOrg_patch | lark-mcp__corehr_v2_customOrg_patch |
| corehr.v2.customOrg.query | corehr_v2_customOrg_query | lark-mcp__corehr_v2_customOrg_query |
| corehr.v2.customOrg.updateRule | corehr_v2_customOrg_updateRule | lark-mcp__corehr_v2_customOrg_updateRule |
| corehr.v2.defaultCostCenter.batchQuery | corehr_v2_defaultCostCenter_batchQuery | lark-mcp__corehr_v2_defaultCostCenter_batchQuery |
| corehr.v2.defaultCostCenter.createVersion | corehr_v2_defaultCostCenter_createVersion | lark-mcp__corehr_v2_defaultCostCenter_createVersion |
| corehr.v2.defaultCostCenter.removeVersion | corehr_v2_defaultCostCenter_removeVersion | lark-mcp__corehr_v2_defaultCostCenter_removeVersion |
| corehr.v2.defaultCostCenter.updateVersion | corehr_v2_defaultCostCenter_updateVersion | lark-mcp__corehr_v2_defaultCostCenter_updateVersion |
| corehr.v2.department.batchGet | corehr_v2_department_batchGet | lark-mcp__corehr_v2_department_batchGet |
| corehr.v2.department.delete | corehr_v2_department_delete | lark-mcp__corehr_v2_department_delete |
| corehr.v2.department.parents | corehr_v2_department_parents | lark-mcp__corehr_v2_department_parents |
| corehr.v2.department.patch | corehr_v2_department_patch | lark-mcp__corehr_v2_department_patch |
| corehr.v2.department.queryMultiTimeline | corehr_v2_department_queryMultiTimeline | lark-mcp__corehr_v2_department_queryMultiTimeline |
| corehr.v2.department.queryOperationLogs | corehr_v2_department_queryOperationLogs | lark-mcp__corehr_v2_department_queryOperationLogs |
| corehr.v2.department.queryRecentChange | corehr_v2_department_queryRecentChange | lark-mcp__corehr_v2_department_queryRecentChange |
| corehr.v2.department.queryTimeline | corehr_v2_department_queryTimeline | lark-mcp__corehr_v2_department_queryTimeline |
| corehr.v2.department.search | corehr_v2_department_search | lark-mcp__corehr_v2_department_search |
| corehr.v2.department.tree | corehr_v2_department_tree | lark-mcp__corehr_v2_department_tree |
| corehr.v2.employee.batchGet | corehr_v2_employee_batchGet | lark-mcp__corehr_v2_employee_batchGet |
| corehr.v2.employee.create | corehr_v2_employee_create | lark-mcp__corehr_v2_employee_create |
| corehr.v2.employee.search | corehr_v2_employee_search | lark-mcp__corehr_v2_employee_search |
| corehr.v2.employeesAdditionalJob.batch | corehr_v2_employeesAdditionalJob_batch | lark-mcp__corehr_v2_employeesAdditionalJob_batch |
| corehr.v2.employeesAdditionalJob.create | corehr_v2_employeesAdditionalJob_create | lark-mcp__corehr_v2_employeesAdditionalJob_create |
| corehr.v2.employeesAdditionalJob.delete | corehr_v2_employeesAdditionalJob_delete | lark-mcp__corehr_v2_employeesAdditionalJob_delete |
| corehr.v2.employeesAdditionalJob.patch | corehr_v2_employeesAdditionalJob_patch | lark-mcp__corehr_v2_employeesAdditionalJob_patch |
| corehr.v2.employeesBp.batchGet | corehr_v2_employeesBp_batchGet | lark-mcp__corehr_v2_employeesBp_batchGet |
| corehr.v2.employeesInternationalAssignment.create | corehr_v2_employeesInternationalAssignment_create | lark-mcp__corehr_v2_employeesInternationalAssignment_create |
| corehr.v2.employeesInternationalAssignment.delete | corehr_v2_employeesInternationalAssignment_delete | lark-mcp__corehr_v2_employeesInternationalAssignment_delete |
| corehr.v2.employeesInternationalAssignment.list | corehr_v2_employeesInternationalAssignment_list | lark-mcp__corehr_v2_employeesInternationalAssignment_list |
| corehr.v2.employeesInternationalAssignment.patch | corehr_v2_employeesInternationalAssignment_patch | lark-mcp__corehr_v2_employeesInternationalAssignment_patch |
| corehr.v2.employeesJobData.batchGet | corehr_v2_employeesJobData_batchGet | lark-mcp__corehr_v2_employeesJobData_batchGet |
| corehr.v2.employeesJobData.query | corehr_v2_employeesJobData_query | lark-mcp__corehr_v2_employeesJobData_query |
| corehr.v2.enum.search | corehr_v2_enum_search | lark-mcp__corehr_v2_enum_search |
| corehr.v2.job.get | corehr_v2_job_get | lark-mcp__corehr_v2_job_get |
| corehr.v2.job.list | corehr_v2_job_list | lark-mcp__corehr_v2_job_list |
| corehr.v2.job.queryRecentChange | corehr_v2_job_queryRecentChange | lark-mcp__corehr_v2_job_queryRecentChange |
| corehr.v2.jobChange.create | corehr_v2_jobChange_create | lark-mcp__corehr_v2_jobChange_create |
| corehr.v2.jobChange.revoke | corehr_v2_jobChange_revoke | lark-mcp__corehr_v2_jobChange_revoke |
| corehr.v2.jobChange.search | corehr_v2_jobChange_search | lark-mcp__corehr_v2_jobChange_search |
| corehr.v2.jobFamily.batchGet | corehr_v2_jobFamily_batchGet | lark-mcp__corehr_v2_jobFamily_batchGet |
| corehr.v2.jobGrade.create | corehr_v2_jobGrade_create | lark-mcp__corehr_v2_jobGrade_create |
| corehr.v2.jobGrade.delete | corehr_v2_jobGrade_delete | lark-mcp__corehr_v2_jobGrade_delete |
| corehr.v2.jobGrade.patch | corehr_v2_jobGrade_patch | lark-mcp__corehr_v2_jobGrade_patch |
| corehr.v2.jobGrade.query | corehr_v2_jobGrade_query | lark-mcp__corehr_v2_jobGrade_query |
| corehr.v2.jobLevel.batchGet | corehr_v2_jobLevel_batchGet | lark-mcp__corehr_v2_jobLevel_batchGet |
| corehr.v2.location.active | corehr_v2_location_active | lark-mcp__corehr_v2_location_active |
| corehr.v2.location.batchGet | corehr_v2_location_batchGet | lark-mcp__corehr_v2_location_batchGet |
| corehr.v2.location.patch | corehr_v2_location_patch | lark-mcp__corehr_v2_location_patch |
| corehr.v2.location.queryRecentChange | corehr_v2_location_queryRecentChange | lark-mcp__corehr_v2_location_queryRecentChange |
| corehr.v2.locationAddress.create | corehr_v2_locationAddress_create | lark-mcp__corehr_v2_locationAddress_create |
| corehr.v2.locationAddress.delete | corehr_v2_locationAddress_delete | lark-mcp__corehr_v2_locationAddress_delete |
| corehr.v2.locationAddress.patch | corehr_v2_locationAddress_patch | lark-mcp__corehr_v2_locationAddress_patch |
| corehr.v2.offboarding.edit | corehr_v2_offboarding_edit | lark-mcp__corehr_v2_offboarding_edit |
| corehr.v2.offboarding.revoke | corehr_v2_offboarding_revoke | lark-mcp__corehr_v2_offboarding_revoke |
| corehr.v2.offboarding.submitV2 | corehr_v2_offboarding_submitV2 | lark-mcp__corehr_v2_offboarding_submitV2 |
| corehr.v2.person.create | corehr_v2_person_create | lark-mcp__corehr_v2_person_create |
| corehr.v2.person.patch | corehr_v2_person_patch | lark-mcp__corehr_v2_person_patch |
| corehr.v2.preHire.complete | corehr_v2_preHire_complete | lark-mcp__corehr_v2_preHire_complete |
| corehr.v2.preHire.create | corehr_v2_preHire_create | lark-mcp__corehr_v2_preHire_create |
| corehr.v2.preHire.delete | corehr_v2_preHire_delete | lark-mcp__corehr_v2_preHire_delete |
| corehr.v2.preHire.patch | corehr_v2_preHire_patch | lark-mcp__corehr_v2_preHire_patch |
| corehr.v2.preHire.query | corehr_v2_preHire_query | lark-mcp__corehr_v2_preHire_query |
| corehr.v2.preHire.restoreFlowInstance | corehr_v2_preHire_restoreFlowInstance | lark-mcp__corehr_v2_preHire_restoreFlowInstance |
| corehr.v2.preHire.search | corehr_v2_preHire_search | lark-mcp__corehr_v2_preHire_search |
| corehr.v2.preHire.transitTask | corehr_v2_preHire_transitTask | lark-mcp__corehr_v2_preHire_transitTask |
| corehr.v2.preHire.withdrawOnboarding | corehr_v2_preHire_withdrawOnboarding | lark-mcp__corehr_v2_preHire_withdrawOnboarding |
| corehr.v2.process.get | corehr_v2_process_get | lark-mcp__corehr_v2_process_get |
| corehr.v2.process.list | corehr_v2_process_list | lark-mcp__corehr_v2_process_list |
| corehr.v2.processApprover.update | corehr_v2_processApprover_update | lark-mcp__corehr_v2_processApprover_update |
| corehr.v2.processExtra.update | corehr_v2_processExtra_update | lark-mcp__corehr_v2_processExtra_update |
| corehr.v2.processFormVariableData.get | corehr_v2_processFormVariableData_get | lark-mcp__corehr_v2_processFormVariableData_get |
| corehr.v2.processRevoke.update | corehr_v2_processRevoke_update | lark-mcp__corehr_v2_processRevoke_update |
| corehr.v2.processTransfer.update | corehr_v2_processTransfer_update | lark-mcp__corehr_v2_processTransfer_update |
| corehr.v2.processWithdraw.update | corehr_v2_processWithdraw_update | lark-mcp__corehr_v2_processWithdraw_update |
| corehr.v2.reportDetailRow.batchDelete | corehr_v2_reportDetailRow_batchDelete | lark-mcp__corehr_v2_reportDetailRow_batchDelete |
| corehr.v2.reportDetailRow.batchSave | corehr_v2_reportDetailRow_batchSave | lark-mcp__corehr_v2_reportDetailRow_batchSave |
| corehr.v2.workforcePlan.list | corehr_v2_workforcePlan_list | lark-mcp__corehr_v2_workforcePlan_list |
| corehr.v2.workforcePlanDetail.batch | corehr_v2_workforcePlanDetail_batch | lark-mcp__corehr_v2_workforcePlanDetail_batch |
| corehr.v2.workforcePlanDetail.batchV2 | corehr_v2_workforcePlanDetail_batchV2 | lark-mcp__corehr_v2_workforcePlanDetail_batchV2 |
| corehr.v2.workforcePlanDetailRow.batchDelete | corehr_v2_workforcePlanDetailRow_batchDelete | lark-mcp__corehr_v2_workforcePlanDetailRow_batchDelete |
| corehr.v2.workforcePlanDetailRow.batchSave | corehr_v2_workforcePlanDetailRow_batchSave | lark-mcp__corehr_v2_workforcePlanDetailRow_batchSave |

## directoryV1 (21)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| directory.v1.collaborationRule.create | directory_v1_collaborationRule_create | lark-mcp__directory_v1_collaborationRule_create |
| directory.v1.collaborationRule.delete | directory_v1_collaborationRule_delete | lark-mcp__directory_v1_collaborationRule_delete |
| directory.v1.collaborationRule.list | directory_v1_collaborationRule_list | lark-mcp__directory_v1_collaborationRule_list |
| directory.v1.collaborationRule.update | directory_v1_collaborationRule_update | lark-mcp__directory_v1_collaborationRule_update |
| directory.v1.collaborationTenant.list | directory_v1_collaborationTenant_list | lark-mcp__directory_v1_collaborationTenant_list |
| directory.v1.collborationShareEntity.list | directory_v1_collborationShareEntity_list | lark-mcp__directory_v1_collborationShareEntity_list |
| directory.v1.department.create | directory_v1_department_create | lark-mcp__directory_v1_department_create |
| directory.v1.department.delete | directory_v1_department_delete | lark-mcp__directory_v1_department_delete |
| directory.v1.department.filter | directory_v1_department_filter | lark-mcp__directory_v1_department_filter |
| directory.v1.department.mget | directory_v1_department_mget | lark-mcp__directory_v1_department_mget |
| directory.v1.department.patch | directory_v1_department_patch | lark-mcp__directory_v1_department_patch |
| directory.v1.department.search | directory_v1_department_search | lark-mcp__directory_v1_department_search |
| directory.v1.employee.create | directory_v1_employee_create | lark-mcp__directory_v1_employee_create |
| directory.v1.employee.delete | directory_v1_employee_delete | lark-mcp__directory_v1_employee_delete |
| directory.v1.employee.filter | directory_v1_employee_filter | lark-mcp__directory_v1_employee_filter |
| directory.v1.employee.mget | directory_v1_employee_mget | lark-mcp__directory_v1_employee_mget |
| directory.v1.employee.patch | directory_v1_employee_patch | lark-mcp__directory_v1_employee_patch |
| directory.v1.employee.regular | directory_v1_employee_regular | lark-mcp__directory_v1_employee_regular |
| directory.v1.employee.resurrect | directory_v1_employee_resurrect | lark-mcp__directory_v1_employee_resurrect |
| directory.v1.employee.search | directory_v1_employee_search | lark-mcp__directory_v1_employee_search |
| directory.v1.employee.toBeResigned | directory_v1_employee_toBeResigned | lark-mcp__directory_v1_employee_toBeResigned |

## docsV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| docs.v1.content.get | docs_v1_content_get | lark-mcp__docs_v1_content_get |

## docxV1 (19)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| docx.v1.chatAnnouncement.get | docx_v1_chatAnnouncement_get | lark-mcp__docx_v1_chatAnnouncement_get |
| docx.v1.chatAnnouncementBlock.batchUpdate | docx_v1_chatAnnouncementBlock_batchUpdate | lark-mcp__docx_v1_chatAnnouncementBlock_batchUpdate |
| docx.v1.chatAnnouncementBlock.get | docx_v1_chatAnnouncementBlock_get | lark-mcp__docx_v1_chatAnnouncementBlock_get |
| docx.v1.chatAnnouncementBlock.list | docx_v1_chatAnnouncementBlock_list | lark-mcp__docx_v1_chatAnnouncementBlock_list |
| docx.v1.chatAnnouncementBlockChildren.batchDelete | docx_v1_chatAnnouncementBlockChildren_batchDelete | lark-mcp__docx_v1_chatAnnouncementBlockChildren_batchDelete |
| docx.v1.chatAnnouncementBlockChildren.create | docx_v1_chatAnnouncementBlockChildren_create | lark-mcp__docx_v1_chatAnnouncementBlockChildren_create |
| docx.v1.chatAnnouncementBlockChildren.get | docx_v1_chatAnnouncementBlockChildren_get | lark-mcp__docx_v1_chatAnnouncementBlockChildren_get |
| docx.v1.document.convert | docx_v1_document_convert | lark-mcp__docx_v1_document_convert |
| docx.v1.document.create | docx_v1_document_create | lark-mcp__docx_v1_document_create |
| docx.v1.document.get | docx_v1_document_get | lark-mcp__docx_v1_document_get |
| docx.v1.document.rawContent | docx_v1_document_rawContent | lark-mcp__docx_v1_document_rawContent |
| docx.v1.documentBlock.batchUpdate | docx_v1_documentBlock_batchUpdate | lark-mcp__docx_v1_documentBlock_batchUpdate |
| docx.v1.documentBlock.get | docx_v1_documentBlock_get | lark-mcp__docx_v1_documentBlock_get |
| docx.v1.documentBlock.list | docx_v1_documentBlock_list | lark-mcp__docx_v1_documentBlock_list |
| docx.v1.documentBlock.patch | docx_v1_documentBlock_patch | lark-mcp__docx_v1_documentBlock_patch |
| docx.v1.documentBlockChildren.batchDelete | docx_v1_documentBlockChildren_batchDelete | lark-mcp__docx_v1_documentBlockChildren_batchDelete |
| docx.v1.documentBlockChildren.create | docx_v1_documentBlockChildren_create | lark-mcp__docx_v1_documentBlockChildren_create |
| docx.v1.documentBlockChildren.get | docx_v1_documentBlockChildren_get | lark-mcp__docx_v1_documentBlockChildren_get |
| docx.v1.documentBlockDescendant.create | docx_v1_documentBlockDescendant_create | lark-mcp__docx_v1_documentBlockDescendant_create |

## driveV1 (49)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| drive.v1.exportTask.create | drive_v1_exportTask_create | lark-mcp__drive_v1_exportTask_create |
| drive.v1.exportTask.get | drive_v1_exportTask_get | lark-mcp__drive_v1_exportTask_get |
| drive.v1.file.copy | drive_v1_file_copy | lark-mcp__drive_v1_file_copy |
| drive.v1.file.createFolder | drive_v1_file_createFolder | lark-mcp__drive_v1_file_createFolder |
| drive.v1.file.createShortcut | drive_v1_file_createShortcut | lark-mcp__drive_v1_file_createShortcut |
| drive.v1.file.delete | drive_v1_file_delete | lark-mcp__drive_v1_file_delete |
| drive.v1.file.deleteSubscribe | drive_v1_file_deleteSubscribe | lark-mcp__drive_v1_file_deleteSubscribe |
| drive.v1.file.getSubscribe | drive_v1_file_getSubscribe | lark-mcp__drive_v1_file_getSubscribe |
| drive.v1.file.list | drive_v1_file_list | lark-mcp__drive_v1_file_list |
| drive.v1.file.move | drive_v1_file_move | lark-mcp__drive_v1_file_move |
| drive.v1.file.subscribe | drive_v1_file_subscribe | lark-mcp__drive_v1_file_subscribe |
| drive.v1.file.taskCheck | drive_v1_file_taskCheck | lark-mcp__drive_v1_file_taskCheck |
| drive.v1.file.uploadFinish | drive_v1_file_uploadFinish | lark-mcp__drive_v1_file_uploadFinish |
| drive.v1.file.uploadPrepare | drive_v1_file_uploadPrepare | lark-mcp__drive_v1_file_uploadPrepare |
| drive.v1.fileComment.batchQuery | drive_v1_fileComment_batchQuery | lark-mcp__drive_v1_fileComment_batchQuery |
| drive.v1.fileComment.create | drive_v1_fileComment_create | lark-mcp__drive_v1_fileComment_create |
| drive.v1.fileComment.get | drive_v1_fileComment_get | lark-mcp__drive_v1_fileComment_get |
| drive.v1.fileComment.list | drive_v1_fileComment_list | lark-mcp__drive_v1_fileComment_list |
| drive.v1.fileComment.patch | drive_v1_fileComment_patch | lark-mcp__drive_v1_fileComment_patch |
| drive.v1.fileCommentReply.delete | drive_v1_fileCommentReply_delete | lark-mcp__drive_v1_fileCommentReply_delete |
| drive.v1.fileCommentReply.list | drive_v1_fileCommentReply_list | lark-mcp__drive_v1_fileCommentReply_list |
| drive.v1.fileCommentReply.update | drive_v1_fileCommentReply_update | lark-mcp__drive_v1_fileCommentReply_update |
| drive.v1.fileStatistics.get | drive_v1_fileStatistics_get | lark-mcp__drive_v1_fileStatistics_get |
| drive.v1.fileSubscription.create | drive_v1_fileSubscription_create | lark-mcp__drive_v1_fileSubscription_create |
| drive.v1.fileSubscription.get | drive_v1_fileSubscription_get | lark-mcp__drive_v1_fileSubscription_get |
| drive.v1.fileSubscription.patch | drive_v1_fileSubscription_patch | lark-mcp__drive_v1_fileSubscription_patch |
| drive.v1.fileVersion.create | drive_v1_fileVersion_create | lark-mcp__drive_v1_fileVersion_create |
| drive.v1.fileVersion.delete | drive_v1_fileVersion_delete | lark-mcp__drive_v1_fileVersion_delete |
| drive.v1.fileVersion.get | drive_v1_fileVersion_get | lark-mcp__drive_v1_fileVersion_get |
| drive.v1.fileVersion.list | drive_v1_fileVersion_list | lark-mcp__drive_v1_fileVersion_list |
| drive.v1.fileViewRecord.list | drive_v1_fileViewRecord_list | lark-mcp__drive_v1_fileViewRecord_list |
| drive.v1.importTask.create | drive_v1_importTask_create | lark-mcp__drive_v1_importTask_create |
| drive.v1.importTask.get | drive_v1_importTask_get | lark-mcp__drive_v1_importTask_get |
| drive.v1.media.batchGetTmpDownloadUrl | drive_v1_media_batchGetTmpDownloadUrl | lark-mcp__drive_v1_media_batchGetTmpDownloadUrl |
| drive.v1.media.uploadFinish | drive_v1_media_uploadFinish | lark-mcp__drive_v1_media_uploadFinish |
| drive.v1.media.uploadPrepare | drive_v1_media_uploadPrepare | lark-mcp__drive_v1_media_uploadPrepare |
| drive.v1.meta.batchQuery | drive_v1_meta_batchQuery | lark-mcp__drive_v1_meta_batchQuery |
| drive.v1.permissionMember.auth | drive_v1_permissionMember_auth | lark-mcp__drive_v1_permissionMember_auth |
| drive.v1.permissionMember.batchCreate | drive_v1_permissionMember_batchCreate | lark-mcp__drive_v1_permissionMember_batchCreate |
| drive.v1.permissionMember.create | drive_v1_permissionMember_create | lark-mcp__drive_v1_permissionMember_create |
| drive.v1.permissionMember.delete | drive_v1_permissionMember_delete | lark-mcp__drive_v1_permissionMember_delete |
| drive.v1.permissionMember.list | drive_v1_permissionMember_list | lark-mcp__drive_v1_permissionMember_list |
| drive.v1.permissionMember.transferOwner | drive_v1_permissionMember_transferOwner | lark-mcp__drive_v1_permissionMember_transferOwner |
| drive.v1.permissionMember.update | drive_v1_permissionMember_update | lark-mcp__drive_v1_permissionMember_update |
| drive.v1.permissionPublic.get | drive_v1_permissionPublic_get | lark-mcp__drive_v1_permissionPublic_get |
| drive.v1.permissionPublic.patch | drive_v1_permissionPublic_patch | lark-mcp__drive_v1_permissionPublic_patch |
| drive.v1.permissionPublicPassword.create | drive_v1_permissionPublicPassword_create | lark-mcp__drive_v1_permissionPublicPassword_create |
| drive.v1.permissionPublicPassword.delete | drive_v1_permissionPublicPassword_delete | lark-mcp__drive_v1_permissionPublicPassword_delete |
| drive.v1.permissionPublicPassword.update | drive_v1_permissionPublicPassword_update | lark-mcp__drive_v1_permissionPublicPassword_update |

## driveV2 (3)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| drive.v2.fileLike.list | drive_v2_fileLike_list | lark-mcp__drive_v2_fileLike_list |
| drive.v2.permissionPublic.get | drive_v2_permissionPublic_get | lark-mcp__drive_v2_permissionPublic_get |
| drive.v2.permissionPublic.patch | drive_v2_permissionPublic_patch | lark-mcp__drive_v2_permissionPublic_patch |

## ehrV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| ehr.v1.employee.list | ehr_v1_employee_list | lark-mcp__ehr_v1_employee_list |

## eventV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| event.v1.outboundIp.list | event_v1_outboundIp_list | lark-mcp__event_v1_outboundIp_list |

## helpdeskV1 (8)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| helpdesk.v1.notification.cancelApprove | helpdesk_v1_notification_cancelApprove | lark-mcp__helpdesk_v1_notification_cancelApprove |
| helpdesk.v1.notification.cancelSend | helpdesk_v1_notification_cancelSend | lark-mcp__helpdesk_v1_notification_cancelSend |
| helpdesk.v1.notification.create | helpdesk_v1_notification_create | lark-mcp__helpdesk_v1_notification_create |
| helpdesk.v1.notification.executeSend | helpdesk_v1_notification_executeSend | lark-mcp__helpdesk_v1_notification_executeSend |
| helpdesk.v1.notification.get | helpdesk_v1_notification_get | lark-mcp__helpdesk_v1_notification_get |
| helpdesk.v1.notification.patch | helpdesk_v1_notification_patch | lark-mcp__helpdesk_v1_notification_patch |
| helpdesk.v1.notification.preview | helpdesk_v1_notification_preview | lark-mcp__helpdesk_v1_notification_preview |
| helpdesk.v1.notification.submitApprove | helpdesk_v1_notification_submitApprove | lark-mcp__helpdesk_v1_notification_submitApprove |

## hireV1 (175)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| hire.v1.advertisement.publish | hire_v1_advertisement_publish | lark-mcp__hire_v1_advertisement_publish |
| hire.v1.agency.batchQuery | hire_v1_agency_batchQuery | lark-mcp__hire_v1_agency_batchQuery |
| hire.v1.agency.get | hire_v1_agency_get | lark-mcp__hire_v1_agency_get |
| hire.v1.agency.getAgencyAccount | hire_v1_agency_getAgencyAccount | lark-mcp__hire_v1_agency_getAgencyAccount |
| hire.v1.agency.operateAgencyAccount | hire_v1_agency_operateAgencyAccount | lark-mcp__hire_v1_agency_operateAgencyAccount |
| hire.v1.agency.protect | hire_v1_agency_protect | lark-mcp__hire_v1_agency_protect |
| hire.v1.agency.protectSearch | hire_v1_agency_protectSearch | lark-mcp__hire_v1_agency_protectSearch |
| hire.v1.agency.query | hire_v1_agency_query | lark-mcp__hire_v1_agency_query |
| hire.v1.application.cancelOnboard | hire_v1_application_cancelOnboard | lark-mcp__hire_v1_application_cancelOnboard |
| hire.v1.application.create | hire_v1_application_create | lark-mcp__hire_v1_application_create |
| hire.v1.application.get | hire_v1_application_get | lark-mcp__hire_v1_application_get |
| hire.v1.application.getDetail | hire_v1_application_getDetail | lark-mcp__hire_v1_application_getDetail |
| hire.v1.application.list | hire_v1_application_list | lark-mcp__hire_v1_application_list |
| hire.v1.application.offer | hire_v1_application_offer | lark-mcp__hire_v1_application_offer |
| hire.v1.application.recover | hire_v1_application_recover | lark-mcp__hire_v1_application_recover |
| hire.v1.application.terminate | hire_v1_application_terminate | lark-mcp__hire_v1_application_terminate |
| hire.v1.application.transferOnboard | hire_v1_application_transferOnboard | lark-mcp__hire_v1_application_transferOnboard |
| hire.v1.application.transferStage | hire_v1_application_transferStage | lark-mcp__hire_v1_application_transferStage |
| hire.v1.applicationInterview.list | hire_v1_applicationInterview_list | lark-mcp__hire_v1_applicationInterview_list |
| hire.v1.attachment.get | hire_v1_attachment_get | lark-mcp__hire_v1_attachment_get |
| hire.v1.attachment.preview | hire_v1_attachment_preview | lark-mcp__hire_v1_attachment_preview |
| hire.v1.backgroundCheckOrder.list | hire_v1_backgroundCheckOrder_list | lark-mcp__hire_v1_backgroundCheckOrder_list |
| hire.v1.diversityInclusion.search | hire_v1_diversityInclusion_search | lark-mcp__hire_v1_diversityInclusion_search |
| hire.v1.ecoAccountCustomField.batchDelete | hire_v1_ecoAccountCustomField_batchDelete | lark-mcp__hire_v1_ecoAccountCustomField_batchDelete |
| hire.v1.ecoAccountCustomField.batchUpdate | hire_v1_ecoAccountCustomField_batchUpdate | lark-mcp__hire_v1_ecoAccountCustomField_batchUpdate |
| hire.v1.ecoAccountCustomField.create | hire_v1_ecoAccountCustomField_create | lark-mcp__hire_v1_ecoAccountCustomField_create |
| hire.v1.ecoBackgroundCheck.cancel | hire_v1_ecoBackgroundCheck_cancel | lark-mcp__hire_v1_ecoBackgroundCheck_cancel |
| hire.v1.ecoBackgroundCheck.updateProgress | hire_v1_ecoBackgroundCheck_updateProgress | lark-mcp__hire_v1_ecoBackgroundCheck_updateProgress |
| hire.v1.ecoBackgroundCheck.updateResult | hire_v1_ecoBackgroundCheck_updateResult | lark-mcp__hire_v1_ecoBackgroundCheck_updateResult |
| hire.v1.ecoBackgroundCheckCustomField.batchDelete | hire_v1_ecoBackgroundCheckCustomField_batchDelete | lark-mcp__hire_v1_ecoBackgroundCheckCustomField_batchDelete |
| hire.v1.ecoBackgroundCheckCustomField.batchUpdate | hire_v1_ecoBackgroundCheckCustomField_batchUpdate | lark-mcp__hire_v1_ecoBackgroundCheckCustomField_batchUpdate |
| hire.v1.ecoBackgroundCheckCustomField.create | hire_v1_ecoBackgroundCheckCustomField_create | lark-mcp__hire_v1_ecoBackgroundCheckCustomField_create |
| hire.v1.ecoBackgroundCheckPackage.batchDelete | hire_v1_ecoBackgroundCheckPackage_batchDelete | lark-mcp__hire_v1_ecoBackgroundCheckPackage_batchDelete |
| hire.v1.ecoBackgroundCheckPackage.batchUpdate | hire_v1_ecoBackgroundCheckPackage_batchUpdate | lark-mcp__hire_v1_ecoBackgroundCheckPackage_batchUpdate |
| hire.v1.ecoBackgroundCheckPackage.create | hire_v1_ecoBackgroundCheckPackage_create | lark-mcp__hire_v1_ecoBackgroundCheckPackage_create |
| hire.v1.ecoExam.loginInfo | hire_v1_ecoExam_loginInfo | lark-mcp__hire_v1_ecoExam_loginInfo |
| hire.v1.ecoExam.updateResult | hire_v1_ecoExam_updateResult | lark-mcp__hire_v1_ecoExam_updateResult |
| hire.v1.ecoExamPaper.batchDelete | hire_v1_ecoExamPaper_batchDelete | lark-mcp__hire_v1_ecoExamPaper_batchDelete |
| hire.v1.ecoExamPaper.batchUpdate | hire_v1_ecoExamPaper_batchUpdate | lark-mcp__hire_v1_ecoExamPaper_batchUpdate |
| hire.v1.ecoExamPaper.create | hire_v1_ecoExamPaper_create | lark-mcp__hire_v1_ecoExamPaper_create |
| hire.v1.ehrImportTask.patch | hire_v1_ehrImportTask_patch | lark-mcp__hire_v1_ehrImportTask_patch |
| hire.v1.employee.get | hire_v1_employee_get | lark-mcp__hire_v1_employee_get |
| hire.v1.employee.getByApplication | hire_v1_employee_getByApplication | lark-mcp__hire_v1_employee_getByApplication |
| hire.v1.employee.patch | hire_v1_employee_patch | lark-mcp__hire_v1_employee_patch |
| hire.v1.evaluation.list | hire_v1_evaluation_list | lark-mcp__hire_v1_evaluation_list |
| hire.v1.evaluationTask.list | hire_v1_evaluationTask_list | lark-mcp__hire_v1_evaluationTask_list |
| hire.v1.exam.create | hire_v1_exam_create | lark-mcp__hire_v1_exam_create |
| hire.v1.examMarkingTask.list | hire_v1_examMarkingTask_list | lark-mcp__hire_v1_examMarkingTask_list |
| hire.v1.externalApplication.create | hire_v1_externalApplication_create | lark-mcp__hire_v1_externalApplication_create |
| hire.v1.externalApplication.delete | hire_v1_externalApplication_delete | lark-mcp__hire_v1_externalApplication_delete |
| hire.v1.externalApplication.list | hire_v1_externalApplication_list | lark-mcp__hire_v1_externalApplication_list |
| hire.v1.externalApplication.update | hire_v1_externalApplication_update | lark-mcp__hire_v1_externalApplication_update |
| hire.v1.externalBackgroundCheck.batchQuery | hire_v1_externalBackgroundCheck_batchQuery | lark-mcp__hire_v1_externalBackgroundCheck_batchQuery |
| hire.v1.externalBackgroundCheck.create | hire_v1_externalBackgroundCheck_create | lark-mcp__hire_v1_externalBackgroundCheck_create |
| hire.v1.externalBackgroundCheck.delete | hire_v1_externalBackgroundCheck_delete | lark-mcp__hire_v1_externalBackgroundCheck_delete |
| hire.v1.externalBackgroundCheck.update | hire_v1_externalBackgroundCheck_update | lark-mcp__hire_v1_externalBackgroundCheck_update |
| hire.v1.externalInterview.batchQuery | hire_v1_externalInterview_batchQuery | lark-mcp__hire_v1_externalInterview_batchQuery |
| hire.v1.externalInterview.create | hire_v1_externalInterview_create | lark-mcp__hire_v1_externalInterview_create |
| hire.v1.externalInterview.delete | hire_v1_externalInterview_delete | lark-mcp__hire_v1_externalInterview_delete |
| hire.v1.externalInterview.update | hire_v1_externalInterview_update | lark-mcp__hire_v1_externalInterview_update |
| hire.v1.externalInterviewAssessment.create | hire_v1_externalInterviewAssessment_create | lark-mcp__hire_v1_externalInterviewAssessment_create |
| hire.v1.externalInterviewAssessment.patch | hire_v1_externalInterviewAssessment_patch | lark-mcp__hire_v1_externalInterviewAssessment_patch |
| hire.v1.externalOffer.batchQuery | hire_v1_externalOffer_batchQuery | lark-mcp__hire_v1_externalOffer_batchQuery |
| hire.v1.externalOffer.create | hire_v1_externalOffer_create | lark-mcp__hire_v1_externalOffer_create |
| hire.v1.externalOffer.delete | hire_v1_externalOffer_delete | lark-mcp__hire_v1_externalOffer_delete |
| hire.v1.externalOffer.update | hire_v1_externalOffer_update | lark-mcp__hire_v1_externalOffer_update |
| hire.v1.externalReferralReward.create | hire_v1_externalReferralReward_create | lark-mcp__hire_v1_externalReferralReward_create |
| hire.v1.externalReferralReward.delete | hire_v1_externalReferralReward_delete | lark-mcp__hire_v1_externalReferralReward_delete |
| hire.v1.interview.getByTalent | hire_v1_interview_getByTalent | lark-mcp__hire_v1_interview_getByTalent |
| hire.v1.interview.list | hire_v1_interview_list | lark-mcp__hire_v1_interview_list |
| hire.v1.interviewer.list | hire_v1_interviewer_list | lark-mcp__hire_v1_interviewer_list |
| hire.v1.interviewer.patch | hire_v1_interviewer_patch | lark-mcp__hire_v1_interviewer_patch |
| hire.v1.interviewFeedbackForm.list | hire_v1_interviewFeedbackForm_list | lark-mcp__hire_v1_interviewFeedbackForm_list |
| hire.v1.interviewRecord.get | hire_v1_interviewRecord_get | lark-mcp__hire_v1_interviewRecord_get |
| hire.v1.interviewRecord.list | hire_v1_interviewRecord_list | lark-mcp__hire_v1_interviewRecord_list |
| hire.v1.interviewRecordAttachment.get | hire_v1_interviewRecordAttachment_get | lark-mcp__hire_v1_interviewRecordAttachment_get |
| hire.v1.interviewRegistrationSchema.list | hire_v1_interviewRegistrationSchema_list | lark-mcp__hire_v1_interviewRegistrationSchema_list |
| hire.v1.interviewRoundType.list | hire_v1_interviewRoundType_list | lark-mcp__hire_v1_interviewRoundType_list |
| hire.v1.interviewTask.list | hire_v1_interviewTask_list | lark-mcp__hire_v1_interviewTask_list |
| hire.v1.job.close | hire_v1_job_close | lark-mcp__hire_v1_job_close |
| hire.v1.job.combinedCreate | hire_v1_job_combinedCreate | lark-mcp__hire_v1_job_combinedCreate |
| hire.v1.job.combinedUpdate | hire_v1_job_combinedUpdate | lark-mcp__hire_v1_job_combinedUpdate |
| hire.v1.job.config | hire_v1_job_config | lark-mcp__hire_v1_job_config |
| hire.v1.job.get | hire_v1_job_get | lark-mcp__hire_v1_job_get |
| hire.v1.job.getDetail | hire_v1_job_getDetail | lark-mcp__hire_v1_job_getDetail |
| hire.v1.job.list | hire_v1_job_list | lark-mcp__hire_v1_job_list |
| hire.v1.job.open | hire_v1_job_open | lark-mcp__hire_v1_job_open |
| hire.v1.job.recruiter | hire_v1_job_recruiter | lark-mcp__hire_v1_job_recruiter |
| hire.v1.job.updateConfig | hire_v1_job_updateConfig | lark-mcp__hire_v1_job_updateConfig |
| hire.v1.jobFunction.list | hire_v1_jobFunction_list | lark-mcp__hire_v1_jobFunction_list |
| hire.v1.jobManager.batchUpdate | hire_v1_jobManager_batchUpdate | lark-mcp__hire_v1_jobManager_batchUpdate |
| hire.v1.jobManager.get | hire_v1_jobManager_get | lark-mcp__hire_v1_jobManager_get |
| hire.v1.jobProcess.list | hire_v1_jobProcess_list | lark-mcp__hire_v1_jobProcess_list |
| hire.v1.jobPublishRecord.search | hire_v1_jobPublishRecord_search | lark-mcp__hire_v1_jobPublishRecord_search |
| hire.v1.jobRequirement.create | hire_v1_jobRequirement_create | lark-mcp__hire_v1_jobRequirement_create |
| hire.v1.jobRequirement.delete | hire_v1_jobRequirement_delete | lark-mcp__hire_v1_jobRequirement_delete |
| hire.v1.jobRequirement.list | hire_v1_jobRequirement_list | lark-mcp__hire_v1_jobRequirement_list |
| hire.v1.jobRequirement.listById | hire_v1_jobRequirement_listById | lark-mcp__hire_v1_jobRequirement_listById |
| hire.v1.jobRequirement.update | hire_v1_jobRequirement_update | lark-mcp__hire_v1_jobRequirement_update |
| hire.v1.jobRequirementSchema.list | hire_v1_jobRequirementSchema_list | lark-mcp__hire_v1_jobRequirementSchema_list |
| hire.v1.jobSchema.list | hire_v1_jobSchema_list | lark-mcp__hire_v1_jobSchema_list |
| hire.v1.jobType.list | hire_v1_jobType_list | lark-mcp__hire_v1_jobType_list |
| hire.v1.location.list | hire_v1_location_list | lark-mcp__hire_v1_location_list |
| hire.v1.location.query | hire_v1_location_query | lark-mcp__hire_v1_location_query |
| hire.v1.minutes.get | hire_v1_minutes_get | lark-mcp__hire_v1_minutes_get |
| hire.v1.note.create | hire_v1_note_create | lark-mcp__hire_v1_note_create |
| hire.v1.note.delete | hire_v1_note_delete | lark-mcp__hire_v1_note_delete |
| hire.v1.note.get | hire_v1_note_get | lark-mcp__hire_v1_note_get |
| hire.v1.note.list | hire_v1_note_list | lark-mcp__hire_v1_note_list |
| hire.v1.note.patch | hire_v1_note_patch | lark-mcp__hire_v1_note_patch |
| hire.v1.offer.create | hire_v1_offer_create | lark-mcp__hire_v1_offer_create |
| hire.v1.offer.get | hire_v1_offer_get | lark-mcp__hire_v1_offer_get |
| hire.v1.offer.internOfferStatus | hire_v1_offer_internOfferStatus | lark-mcp__hire_v1_offer_internOfferStatus |
| hire.v1.offer.list | hire_v1_offer_list | lark-mcp__hire_v1_offer_list |
| hire.v1.offer.offerStatus | hire_v1_offer_offerStatus | lark-mcp__hire_v1_offer_offerStatus |
| hire.v1.offer.update | hire_v1_offer_update | lark-mcp__hire_v1_offer_update |
| hire.v1.offerApplicationForm.get | hire_v1_offerApplicationForm_get | lark-mcp__hire_v1_offerApplicationForm_get |
| hire.v1.offerApplicationForm.list | hire_v1_offerApplicationForm_list | lark-mcp__hire_v1_offerApplicationForm_list |
| hire.v1.offerCustomField.update | hire_v1_offerCustomField_update | lark-mcp__hire_v1_offerCustomField_update |
| hire.v1.offerSchema.get | hire_v1_offerSchema_get | lark-mcp__hire_v1_offerSchema_get |
| hire.v1.questionnaire.list | hire_v1_questionnaire_list | lark-mcp__hire_v1_questionnaire_list |
| hire.v1.referral.getByApplication | hire_v1_referral_getByApplication | lark-mcp__hire_v1_referral_getByApplication |
| hire.v1.referral.search | hire_v1_referral_search | lark-mcp__hire_v1_referral_search |
| hire.v1.referralAccount.create | hire_v1_referralAccount_create | lark-mcp__hire_v1_referralAccount_create |
| hire.v1.referralAccount.deactivate | hire_v1_referralAccount_deactivate | lark-mcp__hire_v1_referralAccount_deactivate |
| hire.v1.referralAccount.enable | hire_v1_referralAccount_enable | lark-mcp__hire_v1_referralAccount_enable |
| hire.v1.referralAccount.getAccountAssets | hire_v1_referralAccount_getAccountAssets | lark-mcp__hire_v1_referralAccount_getAccountAssets |
| hire.v1.referralAccount.reconciliation | hire_v1_referralAccount_reconciliation | lark-mcp__hire_v1_referralAccount_reconciliation |
| hire.v1.referralAccount.withdraw | hire_v1_referralAccount_withdraw | lark-mcp__hire_v1_referralAccount_withdraw |
| hire.v1.referralWebsiteJobPost.get | hire_v1_referralWebsiteJobPost_get | lark-mcp__hire_v1_referralWebsiteJobPost_get |
| hire.v1.referralWebsiteJobPost.list | hire_v1_referralWebsiteJobPost_list | lark-mcp__hire_v1_referralWebsiteJobPost_list |
| hire.v1.registrationSchema.list | hire_v1_registrationSchema_list | lark-mcp__hire_v1_registrationSchema_list |
| hire.v1.resumeSource.list | hire_v1_resumeSource_list | lark-mcp__hire_v1_resumeSource_list |
| hire.v1.role.get | hire_v1_role_get | lark-mcp__hire_v1_role_get |
| hire.v1.role.list | hire_v1_role_list | lark-mcp__hire_v1_role_list |
| hire.v1.subject.list | hire_v1_subject_list | lark-mcp__hire_v1_subject_list |
| hire.v1.talent.addToFolder | hire_v1_talent_addToFolder | lark-mcp__hire_v1_talent_addToFolder |
| hire.v1.talent.batchGetId | hire_v1_talent_batchGetId | lark-mcp__hire_v1_talent_batchGetId |
| hire.v1.talent.combinedCreate | hire_v1_talent_combinedCreate | lark-mcp__hire_v1_talent_combinedCreate |
| hire.v1.talent.combinedUpdate | hire_v1_talent_combinedUpdate | lark-mcp__hire_v1_talent_combinedUpdate |
| hire.v1.talent.get | hire_v1_talent_get | lark-mcp__hire_v1_talent_get |
| hire.v1.talent.list | hire_v1_talent_list | lark-mcp__hire_v1_talent_list |
| hire.v1.talent.onboardStatus | hire_v1_talent_onboardStatus | lark-mcp__hire_v1_talent_onboardStatus |
| hire.v1.talent.removeToFolder | hire_v1_talent_removeToFolder | lark-mcp__hire_v1_talent_removeToFolder |
| hire.v1.talent.tag | hire_v1_talent_tag | lark-mcp__hire_v1_talent_tag |
| hire.v1.talentBlocklist.changeTalentBlock | hire_v1_talentBlocklist_changeTalentBlock | lark-mcp__hire_v1_talentBlocklist_changeTalentBlock |
| hire.v1.talentExternalInfo.create | hire_v1_talentExternalInfo_create | lark-mcp__hire_v1_talentExternalInfo_create |
| hire.v1.talentExternalInfo.update | hire_v1_talentExternalInfo_update | lark-mcp__hire_v1_talentExternalInfo_update |
| hire.v1.talentFolder.list | hire_v1_talentFolder_list | lark-mcp__hire_v1_talentFolder_list |
| hire.v1.talentObject.query | hire_v1_talentObject_query | lark-mcp__hire_v1_talentObject_query |
| hire.v1.talentOperationLog.search | hire_v1_talentOperationLog_search | lark-mcp__hire_v1_talentOperationLog_search |
| hire.v1.talentPool.batchChangeTalentPool | hire_v1_talentPool_batchChangeTalentPool | lark-mcp__hire_v1_talentPool_batchChangeTalentPool |
| hire.v1.talentPool.moveTalent | hire_v1_talentPool_moveTalent | lark-mcp__hire_v1_talentPool_moveTalent |
| hire.v1.talentPool.search | hire_v1_talentPool_search | lark-mcp__hire_v1_talentPool_search |
| hire.v1.talentTag.list | hire_v1_talentTag_list | lark-mcp__hire_v1_talentTag_list |
| hire.v1.terminationReason.list | hire_v1_terminationReason_list | lark-mcp__hire_v1_terminationReason_list |
| hire.v1.test.search | hire_v1_test_search | lark-mcp__hire_v1_test_search |
| hire.v1.todo.list | hire_v1_todo_list | lark-mcp__hire_v1_todo_list |
| hire.v1.tripartiteAgreement.create | hire_v1_tripartiteAgreement_create | lark-mcp__hire_v1_tripartiteAgreement_create |
| hire.v1.tripartiteAgreement.delete | hire_v1_tripartiteAgreement_delete | lark-mcp__hire_v1_tripartiteAgreement_delete |
| hire.v1.tripartiteAgreement.list | hire_v1_tripartiteAgreement_list | lark-mcp__hire_v1_tripartiteAgreement_list |
| hire.v1.tripartiteAgreement.update | hire_v1_tripartiteAgreement_update | lark-mcp__hire_v1_tripartiteAgreement_update |
| hire.v1.userRole.list | hire_v1_userRole_list | lark-mcp__hire_v1_userRole_list |
| hire.v1.website.list | hire_v1_website_list | lark-mcp__hire_v1_website_list |
| hire.v1.websiteChannel.create | hire_v1_websiteChannel_create | lark-mcp__hire_v1_websiteChannel_create |
| hire.v1.websiteChannel.delete | hire_v1_websiteChannel_delete | lark-mcp__hire_v1_websiteChannel_delete |
| hire.v1.websiteChannel.list | hire_v1_websiteChannel_list | lark-mcp__hire_v1_websiteChannel_list |
| hire.v1.websiteChannel.update | hire_v1_websiteChannel_update | lark-mcp__hire_v1_websiteChannel_update |
| hire.v1.websiteDelivery.createByAttachment | hire_v1_websiteDelivery_createByAttachment | lark-mcp__hire_v1_websiteDelivery_createByAttachment |
| hire.v1.websiteDelivery.createByResume | hire_v1_websiteDelivery_createByResume | lark-mcp__hire_v1_websiteDelivery_createByResume |
| hire.v1.websiteDeliveryTask.get | hire_v1_websiteDeliveryTask_get | lark-mcp__hire_v1_websiteDeliveryTask_get |
| hire.v1.websiteJobPost.get | hire_v1_websiteJobPost_get | lark-mcp__hire_v1_websiteJobPost_get |
| hire.v1.websiteJobPost.list | hire_v1_websiteJobPost_list | lark-mcp__hire_v1_websiteJobPost_list |
| hire.v1.websiteJobPost.search | hire_v1_websiteJobPost_search | lark-mcp__hire_v1_websiteJobPost_search |
| hire.v1.websiteSiteUser.create | hire_v1_websiteSiteUser_create | lark-mcp__hire_v1_websiteSiteUser_create |

## hireV2 (3)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| hire.v2.interviewRecord.get | hire_v2_interviewRecord_get | lark-mcp__hire_v2_interviewRecord_get |
| hire.v2.interviewRecord.list | hire_v2_interviewRecord_list | lark-mcp__hire_v2_interviewRecord_list |
| hire.v2.talent.get | hire_v2_talent_get | lark-mcp__hire_v2_talent_get |

## humanAuthenticationV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| human_authentication.v1.identity.create | human_authentication_v1_identity_create | lark-mcp__human_authentication_v1_identity_create |

## imV1 (54)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| im.v1.batchMessage.delete | im_v1_batchMessage_delete | lark-mcp__im_v1_batchMessage_delete |
| im.v1.batchMessage.getProgress | im_v1_batchMessage_getProgress | lark-mcp__im_v1_batchMessage_getProgress |
| im.v1.batchMessage.readUser | im_v1_batchMessage_readUser | lark-mcp__im_v1_batchMessage_readUser |
| im.v1.chat.create | im_v1_chat_create | lark-mcp__im_v1_chat_create |
| im.v1.chat.delete | im_v1_chat_delete | lark-mcp__im_v1_chat_delete |
| im.v1.chat.get | im_v1_chat_get | lark-mcp__im_v1_chat_get |
| im.v1.chat.link | im_v1_chat_link | lark-mcp__im_v1_chat_link |
| im.v1.chat.list | im_v1_chat_list | lark-mcp__im_v1_chat_list |
| im.v1.chat.search | im_v1_chat_search | lark-mcp__im_v1_chat_search |
| im.v1.chat.update | im_v1_chat_update | lark-mcp__im_v1_chat_update |
| im.v1.chatAnnouncement.get | im_v1_chatAnnouncement_get | lark-mcp__im_v1_chatAnnouncement_get |
| im.v1.chatAnnouncement.patch | im_v1_chatAnnouncement_patch | lark-mcp__im_v1_chatAnnouncement_patch |
| im.v1.chatManagers.addManagers | im_v1_chatManagers_addManagers | lark-mcp__im_v1_chatManagers_addManagers |
| im.v1.chatManagers.deleteManagers | im_v1_chatManagers_deleteManagers | lark-mcp__im_v1_chatManagers_deleteManagers |
| im.v1.chatMembers.create | im_v1_chatMembers_create | lark-mcp__im_v1_chatMembers_create |
| im.v1.chatMembers.delete | im_v1_chatMembers_delete | lark-mcp__im_v1_chatMembers_delete |
| im.v1.chatMembers.get | im_v1_chatMembers_get | lark-mcp__im_v1_chatMembers_get |
| im.v1.chatMembers.isInChat | im_v1_chatMembers_isInChat | lark-mcp__im_v1_chatMembers_isInChat |
| im.v1.chatMembers.meJoin | im_v1_chatMembers_meJoin | lark-mcp__im_v1_chatMembers_meJoin |
| im.v1.chatMenuItem.patch | im_v1_chatMenuItem_patch | lark-mcp__im_v1_chatMenuItem_patch |
| im.v1.chatMenuTree.create | im_v1_chatMenuTree_create | lark-mcp__im_v1_chatMenuTree_create |
| im.v1.chatMenuTree.delete | im_v1_chatMenuTree_delete | lark-mcp__im_v1_chatMenuTree_delete |
| im.v1.chatMenuTree.get | im_v1_chatMenuTree_get | lark-mcp__im_v1_chatMenuTree_get |
| im.v1.chatMenuTree.sort | im_v1_chatMenuTree_sort | lark-mcp__im_v1_chatMenuTree_sort |
| im.v1.chatModeration.get | im_v1_chatModeration_get | lark-mcp__im_v1_chatModeration_get |
| im.v1.chatModeration.update | im_v1_chatModeration_update | lark-mcp__im_v1_chatModeration_update |
| im.v1.chatTab.create | im_v1_chatTab_create | lark-mcp__im_v1_chatTab_create |
| im.v1.chatTab.deleteTabs | im_v1_chatTab_deleteTabs | lark-mcp__im_v1_chatTab_deleteTabs |
| im.v1.chatTab.listTabs | im_v1_chatTab_listTabs | lark-mcp__im_v1_chatTab_listTabs |
| im.v1.chatTab.sortTabs | im_v1_chatTab_sortTabs | lark-mcp__im_v1_chatTab_sortTabs |
| im.v1.chatTab.updateTabs | im_v1_chatTab_updateTabs | lark-mcp__im_v1_chatTab_updateTabs |
| im.v1.chatTopNotice.deleteTopNotice | im_v1_chatTopNotice_deleteTopNotice | lark-mcp__im_v1_chatTopNotice_deleteTopNotice |
| im.v1.chatTopNotice.putTopNotice | im_v1_chatTopNotice_putTopNotice | lark-mcp__im_v1_chatTopNotice_putTopNotice |
| im.v1.message.create | im_v1_message_create | lark-mcp__im_v1_message_create |
| im.v1.message.delete | im_v1_message_delete | lark-mcp__im_v1_message_delete |
| im.v1.message.forward | im_v1_message_forward | lark-mcp__im_v1_message_forward |
| im.v1.message.get | im_v1_message_get | lark-mcp__im_v1_message_get |
| im.v1.message.list | im_v1_message_list | lark-mcp__im_v1_message_list |
| im.v1.message.mergeForward | im_v1_message_mergeForward | lark-mcp__im_v1_message_mergeForward |
| im.v1.message.patch | im_v1_message_patch | lark-mcp__im_v1_message_patch |
| im.v1.message.pushFollowUp | im_v1_message_pushFollowUp | lark-mcp__im_v1_message_pushFollowUp |
| im.v1.message.readUsers | im_v1_message_readUsers | lark-mcp__im_v1_message_readUsers |
| im.v1.message.reply | im_v1_message_reply | lark-mcp__im_v1_message_reply |
| im.v1.message.update | im_v1_message_update | lark-mcp__im_v1_message_update |
| im.v1.message.urgentApp | im_v1_message_urgentApp | lark-mcp__im_v1_message_urgentApp |
| im.v1.message.urgentPhone | im_v1_message_urgentPhone | lark-mcp__im_v1_message_urgentPhone |
| im.v1.message.urgentSms | im_v1_message_urgentSms | lark-mcp__im_v1_message_urgentSms |
| im.v1.messageReaction.create | im_v1_messageReaction_create | lark-mcp__im_v1_messageReaction_create |
| im.v1.messageReaction.delete | im_v1_messageReaction_delete | lark-mcp__im_v1_messageReaction_delete |
| im.v1.messageReaction.list | im_v1_messageReaction_list | lark-mcp__im_v1_messageReaction_list |
| im.v1.pin.create | im_v1_pin_create | lark-mcp__im_v1_pin_create |
| im.v1.pin.delete | im_v1_pin_delete | lark-mcp__im_v1_pin_delete |
| im.v1.pin.list | im_v1_pin_list | lark-mcp__im_v1_pin_list |
| im.v1.thread.forward | im_v1_thread_forward | lark-mcp__im_v1_thread_forward |

## imV2 (12)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| im.v2.appFeedCard.create | im_v2_appFeedCard_create | lark-mcp__im_v2_appFeedCard_create |
| im.v2.appFeedCardBatch.delete | im_v2_appFeedCardBatch_delete | lark-mcp__im_v2_appFeedCardBatch_delete |
| im.v2.appFeedCardBatch.update | im_v2_appFeedCardBatch_update | lark-mcp__im_v2_appFeedCardBatch_update |
| im.v2.bizEntityTagRelation.create | im_v2_bizEntityTagRelation_create | lark-mcp__im_v2_bizEntityTagRelation_create |
| im.v2.bizEntityTagRelation.get | im_v2_bizEntityTagRelation_get | lark-mcp__im_v2_bizEntityTagRelation_get |
| im.v2.bizEntityTagRelation.update | im_v2_bizEntityTagRelation_update | lark-mcp__im_v2_bizEntityTagRelation_update |
| im.v2.chatButton.update | im_v2_chatButton_update | lark-mcp__im_v2_chatButton_update |
| im.v2.feedCard.botTimeSentive | im_v2_feedCard_botTimeSentive | lark-mcp__im_v2_feedCard_botTimeSentive |
| im.v2.feedCard.patch | im_v2_feedCard_patch | lark-mcp__im_v2_feedCard_patch |
| im.v2.tag.create | im_v2_tag_create | lark-mcp__im_v2_tag_create |
| im.v2.tag.patch | im_v2_tag_patch | lark-mcp__im_v2_tag_patch |
| im.v2.urlPreview.batchUpdate | im_v2_urlPreview_batchUpdate | lark-mcp__im_v2_urlPreview_batchUpdate |

## lingoV1 (12)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| lingo.v1.classification.list | lingo_v1_classification_list | lark-mcp__lingo_v1_classification_list |
| lingo.v1.draft.create | lingo_v1_draft_create | lark-mcp__lingo_v1_draft_create |
| lingo.v1.draft.update | lingo_v1_draft_update | lark-mcp__lingo_v1_draft_update |
| lingo.v1.entity.create | lingo_v1_entity_create | lark-mcp__lingo_v1_entity_create |
| lingo.v1.entity.delete | lingo_v1_entity_delete | lark-mcp__lingo_v1_entity_delete |
| lingo.v1.entity.get | lingo_v1_entity_get | lark-mcp__lingo_v1_entity_get |
| lingo.v1.entity.highlight | lingo_v1_entity_highlight | lark-mcp__lingo_v1_entity_highlight |
| lingo.v1.entity.list | lingo_v1_entity_list | lark-mcp__lingo_v1_entity_list |
| lingo.v1.entity.match | lingo_v1_entity_match | lark-mcp__lingo_v1_entity_match |
| lingo.v1.entity.search | lingo_v1_entity_search | lark-mcp__lingo_v1_entity_search |
| lingo.v1.entity.update | lingo_v1_entity_update | lark-mcp__lingo_v1_entity_update |
| lingo.v1.repo.list | lingo_v1_repo_list | lark-mcp__lingo_v1_repo_list |

## mailV1 (67)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| mail.v1.mailgroup.create | mail_v1_mailgroup_create | lark-mcp__mail_v1_mailgroup_create |
| mail.v1.mailgroup.delete | mail_v1_mailgroup_delete | lark-mcp__mail_v1_mailgroup_delete |
| mail.v1.mailgroup.get | mail_v1_mailgroup_get | lark-mcp__mail_v1_mailgroup_get |
| mail.v1.mailgroup.list | mail_v1_mailgroup_list | lark-mcp__mail_v1_mailgroup_list |
| mail.v1.mailgroup.patch | mail_v1_mailgroup_patch | lark-mcp__mail_v1_mailgroup_patch |
| mail.v1.mailgroup.update | mail_v1_mailgroup_update | lark-mcp__mail_v1_mailgroup_update |
| mail.v1.mailgroupAlias.create | mail_v1_mailgroupAlias_create | lark-mcp__mail_v1_mailgroupAlias_create |
| mail.v1.mailgroupAlias.delete | mail_v1_mailgroupAlias_delete | lark-mcp__mail_v1_mailgroupAlias_delete |
| mail.v1.mailgroupAlias.list | mail_v1_mailgroupAlias_list | lark-mcp__mail_v1_mailgroupAlias_list |
| mail.v1.mailgroupManager.batchCreate | mail_v1_mailgroupManager_batchCreate | lark-mcp__mail_v1_mailgroupManager_batchCreate |
| mail.v1.mailgroupManager.batchDelete | mail_v1_mailgroupManager_batchDelete | lark-mcp__mail_v1_mailgroupManager_batchDelete |
| mail.v1.mailgroupManager.list | mail_v1_mailgroupManager_list | lark-mcp__mail_v1_mailgroupManager_list |
| mail.v1.mailgroupMember.batchCreate | mail_v1_mailgroupMember_batchCreate | lark-mcp__mail_v1_mailgroupMember_batchCreate |
| mail.v1.mailgroupMember.batchDelete | mail_v1_mailgroupMember_batchDelete | lark-mcp__mail_v1_mailgroupMember_batchDelete |
| mail.v1.mailgroupMember.create | mail_v1_mailgroupMember_create | lark-mcp__mail_v1_mailgroupMember_create |
| mail.v1.mailgroupMember.delete | mail_v1_mailgroupMember_delete | lark-mcp__mail_v1_mailgroupMember_delete |
| mail.v1.mailgroupMember.get | mail_v1_mailgroupMember_get | lark-mcp__mail_v1_mailgroupMember_get |
| mail.v1.mailgroupMember.list | mail_v1_mailgroupMember_list | lark-mcp__mail_v1_mailgroupMember_list |
| mail.v1.mailgroupPermissionMember.batchCreate | mail_v1_mailgroupPermissionMember_batchCreate | lark-mcp__mail_v1_mailgroupPermissionMember_batchCreate |
| mail.v1.mailgroupPermissionMember.batchDelete | mail_v1_mailgroupPermissionMember_batchDelete | lark-mcp__mail_v1_mailgroupPermissionMember_batchDelete |
| mail.v1.mailgroupPermissionMember.create | mail_v1_mailgroupPermissionMember_create | lark-mcp__mail_v1_mailgroupPermissionMember_create |
| mail.v1.mailgroupPermissionMember.delete | mail_v1_mailgroupPermissionMember_delete | lark-mcp__mail_v1_mailgroupPermissionMember_delete |
| mail.v1.mailgroupPermissionMember.get | mail_v1_mailgroupPermissionMember_get | lark-mcp__mail_v1_mailgroupPermissionMember_get |
| mail.v1.mailgroupPermissionMember.list | mail_v1_mailgroupPermissionMember_list | lark-mcp__mail_v1_mailgroupPermissionMember_list |
| mail.v1.publicMailbox.create | mail_v1_publicMailbox_create | lark-mcp__mail_v1_publicMailbox_create |
| mail.v1.publicMailbox.delete | mail_v1_publicMailbox_delete | lark-mcp__mail_v1_publicMailbox_delete |
| mail.v1.publicMailbox.get | mail_v1_publicMailbox_get | lark-mcp__mail_v1_publicMailbox_get |
| mail.v1.publicMailbox.list | mail_v1_publicMailbox_list | lark-mcp__mail_v1_publicMailbox_list |
| mail.v1.publicMailbox.patch | mail_v1_publicMailbox_patch | lark-mcp__mail_v1_publicMailbox_patch |
| mail.v1.publicMailbox.removeToRecycleBin | mail_v1_publicMailbox_removeToRecycleBin | lark-mcp__mail_v1_publicMailbox_removeToRecycleBin |
| mail.v1.publicMailbox.update | mail_v1_publicMailbox_update | lark-mcp__mail_v1_publicMailbox_update |
| mail.v1.publicMailboxAlias.create | mail_v1_publicMailboxAlias_create | lark-mcp__mail_v1_publicMailboxAlias_create |
| mail.v1.publicMailboxAlias.delete | mail_v1_publicMailboxAlias_delete | lark-mcp__mail_v1_publicMailboxAlias_delete |
| mail.v1.publicMailboxAlias.list | mail_v1_publicMailboxAlias_list | lark-mcp__mail_v1_publicMailboxAlias_list |
| mail.v1.publicMailboxMember.batchCreate | mail_v1_publicMailboxMember_batchCreate | lark-mcp__mail_v1_publicMailboxMember_batchCreate |
| mail.v1.publicMailboxMember.batchDelete | mail_v1_publicMailboxMember_batchDelete | lark-mcp__mail_v1_publicMailboxMember_batchDelete |
| mail.v1.publicMailboxMember.clear | mail_v1_publicMailboxMember_clear | lark-mcp__mail_v1_publicMailboxMember_clear |
| mail.v1.publicMailboxMember.create | mail_v1_publicMailboxMember_create | lark-mcp__mail_v1_publicMailboxMember_create |
| mail.v1.publicMailboxMember.delete | mail_v1_publicMailboxMember_delete | lark-mcp__mail_v1_publicMailboxMember_delete |
| mail.v1.publicMailboxMember.get | mail_v1_publicMailboxMember_get | lark-mcp__mail_v1_publicMailboxMember_get |
| mail.v1.publicMailboxMember.list | mail_v1_publicMailboxMember_list | lark-mcp__mail_v1_publicMailboxMember_list |
| mail.v1.user.query | mail_v1_user_query | lark-mcp__mail_v1_user_query |
| mail.v1.userMailbox.delete | mail_v1_userMailbox_delete | lark-mcp__mail_v1_userMailbox_delete |
| mail.v1.userMailboxAlias.create | mail_v1_userMailboxAlias_create | lark-mcp__mail_v1_userMailboxAlias_create |
| mail.v1.userMailboxAlias.delete | mail_v1_userMailboxAlias_delete | lark-mcp__mail_v1_userMailboxAlias_delete |
| mail.v1.userMailboxAlias.list | mail_v1_userMailboxAlias_list | lark-mcp__mail_v1_userMailboxAlias_list |
| mail.v1.userMailboxEvent.subscribe | mail_v1_userMailboxEvent_subscribe | lark-mcp__mail_v1_userMailboxEvent_subscribe |
| mail.v1.userMailboxEvent.subscription | mail_v1_userMailboxEvent_subscription | lark-mcp__mail_v1_userMailboxEvent_subscription |
| mail.v1.userMailboxEvent.unsubscribe | mail_v1_userMailboxEvent_unsubscribe | lark-mcp__mail_v1_userMailboxEvent_unsubscribe |
| mail.v1.userMailboxFolder.create | mail_v1_userMailboxFolder_create | lark-mcp__mail_v1_userMailboxFolder_create |
| mail.v1.userMailboxFolder.delete | mail_v1_userMailboxFolder_delete | lark-mcp__mail_v1_userMailboxFolder_delete |
| mail.v1.userMailboxFolder.list | mail_v1_userMailboxFolder_list | lark-mcp__mail_v1_userMailboxFolder_list |
| mail.v1.userMailboxFolder.patch | mail_v1_userMailboxFolder_patch | lark-mcp__mail_v1_userMailboxFolder_patch |
| mail.v1.userMailboxMailContact.create | mail_v1_userMailboxMailContact_create | lark-mcp__mail_v1_userMailboxMailContact_create |
| mail.v1.userMailboxMailContact.delete | mail_v1_userMailboxMailContact_delete | lark-mcp__mail_v1_userMailboxMailContact_delete |
| mail.v1.userMailboxMailContact.list | mail_v1_userMailboxMailContact_list | lark-mcp__mail_v1_userMailboxMailContact_list |
| mail.v1.userMailboxMailContact.patch | mail_v1_userMailboxMailContact_patch | lark-mcp__mail_v1_userMailboxMailContact_patch |
| mail.v1.userMailboxMessage.get | mail_v1_userMailboxMessage_get | lark-mcp__mail_v1_userMailboxMessage_get |
| mail.v1.userMailboxMessage.getByCard | mail_v1_userMailboxMessage_getByCard | lark-mcp__mail_v1_userMailboxMessage_getByCard |
| mail.v1.userMailboxMessage.list | mail_v1_userMailboxMessage_list | lark-mcp__mail_v1_userMailboxMessage_list |
| mail.v1.userMailboxMessage.send | mail_v1_userMailboxMessage_send | lark-mcp__mail_v1_userMailboxMessage_send |
| mail.v1.userMailboxMessageAttachment.downloadUrl | mail_v1_userMailboxMessageAttachment_downloadUrl | lark-mcp__mail_v1_userMailboxMessageAttachment_downloadUrl |
| mail.v1.userMailboxRule.create | mail_v1_userMailboxRule_create | lark-mcp__mail_v1_userMailboxRule_create |
| mail.v1.userMailboxRule.delete | mail_v1_userMailboxRule_delete | lark-mcp__mail_v1_userMailboxRule_delete |
| mail.v1.userMailboxRule.list | mail_v1_userMailboxRule_list | lark-mcp__mail_v1_userMailboxRule_list |
| mail.v1.userMailboxRule.reorder | mail_v1_userMailboxRule_reorder | lark-mcp__mail_v1_userMailboxRule_reorder |
| mail.v1.userMailboxRule.update | mail_v1_userMailboxRule_update | lark-mcp__mail_v1_userMailboxRule_update |

## mdmV1 (2)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| mdm.v1.userAuthDataRelation.bind | mdm_v1_userAuthDataRelation_bind | lark-mcp__mdm_v1_userAuthDataRelation_bind |
| mdm.v1.userAuthDataRelation.unbind | mdm_v1_userAuthDataRelation_unbind | lark-mcp__mdm_v1_userAuthDataRelation_unbind |

## mdmV3 (2)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| mdm.v3.batchCountryRegion.get | mdm_v3_batchCountryRegion_get | lark-mcp__mdm_v3_batchCountryRegion_get |
| mdm.v3.countryRegion.list | mdm_v3_countryRegion_list | lark-mcp__mdm_v3_countryRegion_list |

## minutesV1 (3)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| minutes.v1.minute.get | minutes_v1_minute_get | lark-mcp__minutes_v1_minute_get |
| minutes.v1.minuteMedia.get | minutes_v1_minuteMedia_get | lark-mcp__minutes_v1_minuteMedia_get |
| minutes.v1.minuteStatistics.get | minutes_v1_minuteStatistics_get | lark-mcp__minutes_v1_minuteStatistics_get |

## momentsV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| moments.v1.post.get | moments_v1_post_get | lark-mcp__moments_v1_post_get |

## okrV1 (11)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| okr.v1.okr.batchGet | okr_v1_okr_batchGet | lark-mcp__okr_v1_okr_batchGet |
| okr.v1.period.create | okr_v1_period_create | lark-mcp__okr_v1_period_create |
| okr.v1.period.list | okr_v1_period_list | lark-mcp__okr_v1_period_list |
| okr.v1.period.patch | okr_v1_period_patch | lark-mcp__okr_v1_period_patch |
| okr.v1.periodRule.list | okr_v1_periodRule_list | lark-mcp__okr_v1_periodRule_list |
| okr.v1.progressRecord.create | okr_v1_progressRecord_create | lark-mcp__okr_v1_progressRecord_create |
| okr.v1.progressRecord.delete | okr_v1_progressRecord_delete | lark-mcp__okr_v1_progressRecord_delete |
| okr.v1.progressRecord.get | okr_v1_progressRecord_get | lark-mcp__okr_v1_progressRecord_get |
| okr.v1.progressRecord.update | okr_v1_progressRecord_update | lark-mcp__okr_v1_progressRecord_update |
| okr.v1.review.query | okr_v1_review_query | lark-mcp__okr_v1_review_query |
| okr.v1.userOkr.list | okr_v1_userOkr_list | lark-mcp__okr_v1_userOkr_list |

## opticalCharRecognitionV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| optical_char_recognition.v1.image.basicRecognize | optical_char_recognition_v1_image_basicRecognize | lark-mcp__optical_char_recognition_v1_image_basicRecognize |

## passportV1 (2)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| passport.v1.session.logout | passport_v1_session_logout | lark-mcp__passport_v1_session_logout |
| passport.v1.session.query | passport_v1_session_query | lark-mcp__passport_v1_session_query |

## payrollV1 (6)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| payroll.v1.costAllocationDetail.list | payroll_v1_costAllocationDetail_list | lark-mcp__payroll_v1_costAllocationDetail_list |
| payroll.v1.costAllocationPlan.list | payroll_v1_costAllocationPlan_list | lark-mcp__payroll_v1_costAllocationPlan_list |
| payroll.v1.costAllocationReport.list | payroll_v1_costAllocationReport_list | lark-mcp__payroll_v1_costAllocationReport_list |
| payroll.v1.datasource.list | payroll_v1_datasource_list | lark-mcp__payroll_v1_datasource_list |
| payroll.v1.datasourceRecord.query | payroll_v1_datasourceRecord_query | lark-mcp__payroll_v1_datasourceRecord_query |
| payroll.v1.datasourceRecord.save | payroll_v1_datasourceRecord_save | lark-mcp__payroll_v1_datasourceRecord_save |

## performanceV1 (4)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| performance.v1.reviewData.query | performance_v1_reviewData_query | lark-mcp__performance_v1_reviewData_query |
| performance.v1.semester.list | performance_v1_semester_list | lark-mcp__performance_v1_semester_list |
| performance.v1.stageTask.findByPage | performance_v1_stageTask_findByPage | lark-mcp__performance_v1_stageTask_findByPage |
| performance.v1.stageTask.findByUserList | performance_v1_stageTask_findByUserList | lark-mcp__performance_v1_stageTask_findByUserList |

## performanceV2 (16)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| performance.v2.activity.query | performance_v2_activity_query | lark-mcp__performance_v2_activity_query |
| performance.v2.additionalInformation.import | performance_v2_additionalInformation_import | lark-mcp__performance_v2_additionalInformation_import |
| performance.v2.additionalInformation.query | performance_v2_additionalInformation_query | lark-mcp__performance_v2_additionalInformation_query |
| performance.v2.additionalInformationsBatch.delete | performance_v2_additionalInformationsBatch_delete | lark-mcp__performance_v2_additionalInformationsBatch_delete |
| performance.v2.indicator.query | performance_v2_indicator_query | lark-mcp__performance_v2_indicator_query |
| performance.v2.metricDetail.import | performance_v2_metricDetail_import | lark-mcp__performance_v2_metricDetail_import |
| performance.v2.metricDetail.query | performance_v2_metricDetail_query | lark-mcp__performance_v2_metricDetail_query |
| performance.v2.metricField.query | performance_v2_metricField_query | lark-mcp__performance_v2_metricField_query |
| performance.v2.metricLib.query | performance_v2_metricLib_query | lark-mcp__performance_v2_metricLib_query |
| performance.v2.metricTag.list | performance_v2_metricTag_list | lark-mcp__performance_v2_metricTag_list |
| performance.v2.metricTemplate.query | performance_v2_metricTemplate_query | lark-mcp__performance_v2_metricTemplate_query |
| performance.v2.question.query | performance_v2_question_query | lark-mcp__performance_v2_question_query |
| performance.v2.reviewData.query | performance_v2_reviewData_query | lark-mcp__performance_v2_reviewData_query |
| performance.v2.reviewee.query | performance_v2_reviewee_query | lark-mcp__performance_v2_reviewee_query |
| performance.v2.reviewTemplate.query | performance_v2_reviewTemplate_query | lark-mcp__performance_v2_reviewTemplate_query |
| performance.v2.userGroupUserRel.write | performance_v2_userGroupUserRel_write | lark-mcp__performance_v2_userGroupUserRel_write |

## personalSettingsV1 (6)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| personal_settings.v1.systemStatus.batchClose | personal_settings_v1_systemStatus_batchClose | lark-mcp__personal_settings_v1_systemStatus_batchClose |
| personal_settings.v1.systemStatus.batchOpen | personal_settings_v1_systemStatus_batchOpen | lark-mcp__personal_settings_v1_systemStatus_batchOpen |
| personal_settings.v1.systemStatus.create | personal_settings_v1_systemStatus_create | lark-mcp__personal_settings_v1_systemStatus_create |
| personal_settings.v1.systemStatus.delete | personal_settings_v1_systemStatus_delete | lark-mcp__personal_settings_v1_systemStatus_delete |
| personal_settings.v1.systemStatus.list | personal_settings_v1_systemStatus_list | lark-mcp__personal_settings_v1_systemStatus_list |
| personal_settings.v1.systemStatus.patch | personal_settings_v1_systemStatus_patch | lark-mcp__personal_settings_v1_systemStatus_patch |

## reportV1 (3)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| report.v1.rule.query | report_v1_rule_query | lark-mcp__report_v1_rule_query |
| report.v1.ruleView.remove | report_v1_ruleView_remove | lark-mcp__report_v1_ruleView_remove |
| report.v1.task.query | report_v1_task_query | lark-mcp__report_v1_task_query |

## searchV2 (14)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| search.v2.app.create | search_v2_app_create | lark-mcp__search_v2_app_create |
| search.v2.dataSource.create | search_v2_dataSource_create | lark-mcp__search_v2_dataSource_create |
| search.v2.dataSource.delete | search_v2_dataSource_delete | lark-mcp__search_v2_dataSource_delete |
| search.v2.dataSource.get | search_v2_dataSource_get | lark-mcp__search_v2_dataSource_get |
| search.v2.dataSource.list | search_v2_dataSource_list | lark-mcp__search_v2_dataSource_list |
| search.v2.dataSource.patch | search_v2_dataSource_patch | lark-mcp__search_v2_dataSource_patch |
| search.v2.dataSourceItem.create | search_v2_dataSourceItem_create | lark-mcp__search_v2_dataSourceItem_create |
| search.v2.dataSourceItem.delete | search_v2_dataSourceItem_delete | lark-mcp__search_v2_dataSourceItem_delete |
| search.v2.dataSourceItem.get | search_v2_dataSourceItem_get | lark-mcp__search_v2_dataSourceItem_get |
| search.v2.message.create | search_v2_message_create | lark-mcp__search_v2_message_create |
| search.v2.schema.create | search_v2_schema_create | lark-mcp__search_v2_schema_create |
| search.v2.schema.delete | search_v2_schema_delete | lark-mcp__search_v2_schema_delete |
| search.v2.schema.get | search_v2_schema_get | lark-mcp__search_v2_schema_get |
| search.v2.schema.patch | search_v2_schema_patch | lark-mcp__search_v2_schema_patch |

## securityAndComplianceV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| security_and_compliance.v1.openapiLog.listData | security_and_compliance_v1_openapiLog_listData | lark-mcp__security_and_compliance_v1_openapiLog_listData |

## sheetsV3 (27)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| sheets.v3.spreadsheet.create | sheets_v3_spreadsheet_create | lark-mcp__sheets_v3_spreadsheet_create |
| sheets.v3.spreadsheet.get | sheets_v3_spreadsheet_get | lark-mcp__sheets_v3_spreadsheet_get |
| sheets.v3.spreadsheet.patch | sheets_v3_spreadsheet_patch | lark-mcp__sheets_v3_spreadsheet_patch |
| sheets.v3.spreadsheetSheet.find | sheets_v3_spreadsheetSheet_find | lark-mcp__sheets_v3_spreadsheetSheet_find |
| sheets.v3.spreadsheetSheet.get | sheets_v3_spreadsheetSheet_get | lark-mcp__sheets_v3_spreadsheetSheet_get |
| sheets.v3.spreadsheetSheet.moveDimension | sheets_v3_spreadsheetSheet_moveDimension | lark-mcp__sheets_v3_spreadsheetSheet_moveDimension |
| sheets.v3.spreadsheetSheet.query | sheets_v3_spreadsheetSheet_query | lark-mcp__sheets_v3_spreadsheetSheet_query |
| sheets.v3.spreadsheetSheet.replace | sheets_v3_spreadsheetSheet_replace | lark-mcp__sheets_v3_spreadsheetSheet_replace |
| sheets.v3.spreadsheetSheetFilter.create | sheets_v3_spreadsheetSheetFilter_create | lark-mcp__sheets_v3_spreadsheetSheetFilter_create |
| sheets.v3.spreadsheetSheetFilter.delete | sheets_v3_spreadsheetSheetFilter_delete | lark-mcp__sheets_v3_spreadsheetSheetFilter_delete |
| sheets.v3.spreadsheetSheetFilter.get | sheets_v3_spreadsheetSheetFilter_get | lark-mcp__sheets_v3_spreadsheetSheetFilter_get |
| sheets.v3.spreadsheetSheetFilter.update | sheets_v3_spreadsheetSheetFilter_update | lark-mcp__sheets_v3_spreadsheetSheetFilter_update |
| sheets.v3.spreadsheetSheetFilterView.create | sheets_v3_spreadsheetSheetFilterView_create | lark-mcp__sheets_v3_spreadsheetSheetFilterView_create |
| sheets.v3.spreadsheetSheetFilterView.delete | sheets_v3_spreadsheetSheetFilterView_delete | lark-mcp__sheets_v3_spreadsheetSheetFilterView_delete |
| sheets.v3.spreadsheetSheetFilterView.get | sheets_v3_spreadsheetSheetFilterView_get | lark-mcp__sheets_v3_spreadsheetSheetFilterView_get |
| sheets.v3.spreadsheetSheetFilterView.patch | sheets_v3_spreadsheetSheetFilterView_patch | lark-mcp__sheets_v3_spreadsheetSheetFilterView_patch |
| sheets.v3.spreadsheetSheetFilterView.query | sheets_v3_spreadsheetSheetFilterView_query | lark-mcp__sheets_v3_spreadsheetSheetFilterView_query |
| sheets.v3.spreadsheetSheetFilterViewCondition.create | sheets_v3_spreadsheetSheetFilterViewCondition_create | lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_create |
| sheets.v3.spreadsheetSheetFilterViewCondition.delete | sheets_v3_spreadsheetSheetFilterViewCondition_delete | lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_delete |
| sheets.v3.spreadsheetSheetFilterViewCondition.get | sheets_v3_spreadsheetSheetFilterViewCondition_get | lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_get |
| sheets.v3.spreadsheetSheetFilterViewCondition.query | sheets_v3_spreadsheetSheetFilterViewCondition_query | lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_query |
| sheets.v3.spreadsheetSheetFilterViewCondition.update | sheets_v3_spreadsheetSheetFilterViewCondition_update | lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_update |
| sheets.v3.spreadsheetSheetFloatImage.create | sheets_v3_spreadsheetSheetFloatImage_create | lark-mcp__sheets_v3_spreadsheetSheetFloatImage_create |
| sheets.v3.spreadsheetSheetFloatImage.delete | sheets_v3_spreadsheetSheetFloatImage_delete | lark-mcp__sheets_v3_spreadsheetSheetFloatImage_delete |
| sheets.v3.spreadsheetSheetFloatImage.get | sheets_v3_spreadsheetSheetFloatImage_get | lark-mcp__sheets_v3_spreadsheetSheetFloatImage_get |
| sheets.v3.spreadsheetSheetFloatImage.patch | sheets_v3_spreadsheetSheetFloatImage_patch | lark-mcp__sheets_v3_spreadsheetSheetFloatImage_patch |
| sheets.v3.spreadsheetSheetFloatImage.query | sheets_v3_spreadsheetSheetFloatImage_query | lark-mcp__sheets_v3_spreadsheetSheetFloatImage_query |

## speechToTextV1 (2)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| speech_to_text.v1.speech.fileRecognize | speech_to_text_v1_speech_fileRecognize | lark-mcp__speech_to_text_v1_speech_fileRecognize |
| speech_to_text.v1.speech.streamRecognize | speech_to_text_v1_speech_streamRecognize | lark-mcp__speech_to_text_v1_speech_streamRecognize |

## taskV1 (23)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| task.v1.task.batchDeleteCollaborator | task_v1_task_batchDeleteCollaborator | lark-mcp__task_v1_task_batchDeleteCollaborator |
| task.v1.task.batchDeleteFollower | task_v1_task_batchDeleteFollower | lark-mcp__task_v1_task_batchDeleteFollower |
| task.v1.task.complete | task_v1_task_complete | lark-mcp__task_v1_task_complete |
| task.v1.task.create | task_v1_task_create | lark-mcp__task_v1_task_create |
| task.v1.task.delete | task_v1_task_delete | lark-mcp__task_v1_task_delete |
| task.v1.task.get | task_v1_task_get | lark-mcp__task_v1_task_get |
| task.v1.task.list | task_v1_task_list | lark-mcp__task_v1_task_list |
| task.v1.task.patch | task_v1_task_patch | lark-mcp__task_v1_task_patch |
| task.v1.task.uncomplete | task_v1_task_uncomplete | lark-mcp__task_v1_task_uncomplete |
| task.v1.taskCollaborator.create | task_v1_taskCollaborator_create | lark-mcp__task_v1_taskCollaborator_create |
| task.v1.taskCollaborator.delete | task_v1_taskCollaborator_delete | lark-mcp__task_v1_taskCollaborator_delete |
| task.v1.taskCollaborator.list | task_v1_taskCollaborator_list | lark-mcp__task_v1_taskCollaborator_list |
| task.v1.taskComment.create | task_v1_taskComment_create | lark-mcp__task_v1_taskComment_create |
| task.v1.taskComment.delete | task_v1_taskComment_delete | lark-mcp__task_v1_taskComment_delete |
| task.v1.taskComment.get | task_v1_taskComment_get | lark-mcp__task_v1_taskComment_get |
| task.v1.taskComment.list | task_v1_taskComment_list | lark-mcp__task_v1_taskComment_list |
| task.v1.taskComment.update | task_v1_taskComment_update | lark-mcp__task_v1_taskComment_update |
| task.v1.taskFollower.create | task_v1_taskFollower_create | lark-mcp__task_v1_taskFollower_create |
| task.v1.taskFollower.delete | task_v1_taskFollower_delete | lark-mcp__task_v1_taskFollower_delete |
| task.v1.taskFollower.list | task_v1_taskFollower_list | lark-mcp__task_v1_taskFollower_list |
| task.v1.taskReminder.create | task_v1_taskReminder_create | lark-mcp__task_v1_taskReminder_create |
| task.v1.taskReminder.delete | task_v1_taskReminder_delete | lark-mcp__task_v1_taskReminder_delete |
| task.v1.taskReminder.list | task_v1_taskReminder_list | lark-mcp__task_v1_taskReminder_list |

## taskV2 (51)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| task.v2.attachment.delete | task_v2_attachment_delete | lark-mcp__task_v2_attachment_delete |
| task.v2.attachment.get | task_v2_attachment_get | lark-mcp__task_v2_attachment_get |
| task.v2.attachment.list | task_v2_attachment_list | lark-mcp__task_v2_attachment_list |
| task.v2.comment.create | task_v2_comment_create | lark-mcp__task_v2_comment_create |
| task.v2.comment.delete | task_v2_comment_delete | lark-mcp__task_v2_comment_delete |
| task.v2.comment.get | task_v2_comment_get | lark-mcp__task_v2_comment_get |
| task.v2.comment.list | task_v2_comment_list | lark-mcp__task_v2_comment_list |
| task.v2.comment.patch | task_v2_comment_patch | lark-mcp__task_v2_comment_patch |
| task.v2.customField.add | task_v2_customField_add | lark-mcp__task_v2_customField_add |
| task.v2.customField.create | task_v2_customField_create | lark-mcp__task_v2_customField_create |
| task.v2.customField.get | task_v2_customField_get | lark-mcp__task_v2_customField_get |
| task.v2.customField.list | task_v2_customField_list | lark-mcp__task_v2_customField_list |
| task.v2.customField.patch | task_v2_customField_patch | lark-mcp__task_v2_customField_patch |
| task.v2.customField.remove | task_v2_customField_remove | lark-mcp__task_v2_customField_remove |
| task.v2.customFieldOption.create | task_v2_customFieldOption_create | lark-mcp__task_v2_customFieldOption_create |
| task.v2.customFieldOption.patch | task_v2_customFieldOption_patch | lark-mcp__task_v2_customFieldOption_patch |
| task.v2.section.create | task_v2_section_create | lark-mcp__task_v2_section_create |
| task.v2.section.delete | task_v2_section_delete | lark-mcp__task_v2_section_delete |
| task.v2.section.get | task_v2_section_get | lark-mcp__task_v2_section_get |
| task.v2.section.list | task_v2_section_list | lark-mcp__task_v2_section_list |
| task.v2.section.patch | task_v2_section_patch | lark-mcp__task_v2_section_patch |
| task.v2.section.tasks | task_v2_section_tasks | lark-mcp__task_v2_section_tasks |
| task.v2.task.addDependencies | task_v2_task_addDependencies | lark-mcp__task_v2_task_addDependencies |
| task.v2.task.addMembers | task_v2_task_addMembers | lark-mcp__task_v2_task_addMembers |
| task.v2.task.addReminders | task_v2_task_addReminders | lark-mcp__task_v2_task_addReminders |
| task.v2.task.addTasklist | task_v2_task_addTasklist | lark-mcp__task_v2_task_addTasklist |
| task.v2.task.create | task_v2_task_create | lark-mcp__task_v2_task_create |
| task.v2.task.delete | task_v2_task_delete | lark-mcp__task_v2_task_delete |
| task.v2.task.get | task_v2_task_get | lark-mcp__task_v2_task_get |
| task.v2.task.list | task_v2_task_list | lark-mcp__task_v2_task_list |
| task.v2.task.patch | task_v2_task_patch | lark-mcp__task_v2_task_patch |
| task.v2.task.removeDependencies | task_v2_task_removeDependencies | lark-mcp__task_v2_task_removeDependencies |
| task.v2.task.removeMembers | task_v2_task_removeMembers | lark-mcp__task_v2_task_removeMembers |
| task.v2.task.removeReminders | task_v2_task_removeReminders | lark-mcp__task_v2_task_removeReminders |
| task.v2.task.removeTasklist | task_v2_task_removeTasklist | lark-mcp__task_v2_task_removeTasklist |
| task.v2.task.tasklists | task_v2_task_tasklists | lark-mcp__task_v2_task_tasklists |
| task.v2.tasklist.addMembers | task_v2_tasklist_addMembers | lark-mcp__task_v2_tasklist_addMembers |
| task.v2.tasklist.create | task_v2_tasklist_create | lark-mcp__task_v2_tasklist_create |
| task.v2.tasklist.delete | task_v2_tasklist_delete | lark-mcp__task_v2_tasklist_delete |
| task.v2.tasklist.get | task_v2_tasklist_get | lark-mcp__task_v2_tasklist_get |
| task.v2.tasklist.list | task_v2_tasklist_list | lark-mcp__task_v2_tasklist_list |
| task.v2.tasklist.patch | task_v2_tasklist_patch | lark-mcp__task_v2_tasklist_patch |
| task.v2.tasklist.removeMembers | task_v2_tasklist_removeMembers | lark-mcp__task_v2_tasklist_removeMembers |
| task.v2.tasklist.tasks | task_v2_tasklist_tasks | lark-mcp__task_v2_tasklist_tasks |
| task.v2.tasklistActivitySubscription.create | task_v2_tasklistActivitySubscription_create | lark-mcp__task_v2_tasklistActivitySubscription_create |
| task.v2.tasklistActivitySubscription.delete | task_v2_tasklistActivitySubscription_delete | lark-mcp__task_v2_tasklistActivitySubscription_delete |
| task.v2.tasklistActivitySubscription.get | task_v2_tasklistActivitySubscription_get | lark-mcp__task_v2_tasklistActivitySubscription_get |
| task.v2.tasklistActivitySubscription.list | task_v2_tasklistActivitySubscription_list | lark-mcp__task_v2_tasklistActivitySubscription_list |
| task.v2.tasklistActivitySubscription.patch | task_v2_tasklistActivitySubscription_patch | lark-mcp__task_v2_tasklistActivitySubscription_patch |
| task.v2.taskSubtask.create | task_v2_taskSubtask_create | lark-mcp__task_v2_taskSubtask_create |
| task.v2.taskSubtask.list | task_v2_taskSubtask_list | lark-mcp__task_v2_taskSubtask_list |

## tenantV2 (2)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| tenant.v2.tenant.query | tenant_v2_tenant_query | lark-mcp__tenant_v2_tenant_query |
| tenant.v2.tenantProductAssignInfo.query | tenant_v2_tenantProductAssignInfo_query | lark-mcp__tenant_v2_tenantProductAssignInfo_query |

## translationV1 (2)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| translation.v1.text.detect | translation_v1_text_detect | lark-mcp__translation_v1_text_detect |
| translation.v1.text.translate | translation_v1_text_translate | lark-mcp__translation_v1_text_translate |

## trustPartyV1 (5)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| trust_party.v1.collaborationTenant.get | trust_party_v1_collaborationTenant_get | lark-mcp__trust_party_v1_collaborationTenant_get |
| trust_party.v1.collaborationTenant.list | trust_party_v1_collaborationTenant_list | lark-mcp__trust_party_v1_collaborationTenant_list |
| trust_party.v1.collaborationTenant.visibleOrganization | trust_party_v1_collaborationTenant_visibleOrganization | lark-mcp__trust_party_v1_collaborationTenant_visibleOrganization |
| trust_party.v1.collaborationTenantCollaborationDepartment.get | trust_party_v1_collaborationTenantCollaborationDepartment_get | lark-mcp__trust_party_v1_collaborationTenantCollaborationDepartment_get |
| trust_party.v1.collaborationTenantCollaborationUser.get | trust_party_v1_collaborationTenantCollaborationUser_get | lark-mcp__trust_party_v1_collaborationTenantCollaborationUser_get |

## vcV1 (55)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| vc.v1.alert.list | vc_v1_alert_list | lark-mcp__vc_v1_alert_list |
| vc.v1.export.get | vc_v1_export_get | lark-mcp__vc_v1_export_get |
| vc.v1.export.meetingList | vc_v1_export_meetingList | lark-mcp__vc_v1_export_meetingList |
| vc.v1.export.participantList | vc_v1_export_participantList | lark-mcp__vc_v1_export_participantList |
| vc.v1.export.participantQualityList | vc_v1_export_participantQualityList | lark-mcp__vc_v1_export_participantQualityList |
| vc.v1.export.resourceReservationList | vc_v1_export_resourceReservationList | lark-mcp__vc_v1_export_resourceReservationList |
| vc.v1.meeting.end | vc_v1_meeting_end | lark-mcp__vc_v1_meeting_end |
| vc.v1.meeting.get | vc_v1_meeting_get | lark-mcp__vc_v1_meeting_get |
| vc.v1.meeting.invite | vc_v1_meeting_invite | lark-mcp__vc_v1_meeting_invite |
| vc.v1.meeting.kickout | vc_v1_meeting_kickout | lark-mcp__vc_v1_meeting_kickout |
| vc.v1.meeting.listByNo | vc_v1_meeting_listByNo | lark-mcp__vc_v1_meeting_listByNo |
| vc.v1.meeting.setHost | vc_v1_meeting_setHost | lark-mcp__vc_v1_meeting_setHost |
| vc.v1.meetingList.get | vc_v1_meetingList_get | lark-mcp__vc_v1_meetingList_get |
| vc.v1.meetingRecording.get | vc_v1_meetingRecording_get | lark-mcp__vc_v1_meetingRecording_get |
| vc.v1.meetingRecording.setPermission | vc_v1_meetingRecording_setPermission | lark-mcp__vc_v1_meetingRecording_setPermission |
| vc.v1.meetingRecording.start | vc_v1_meetingRecording_start | lark-mcp__vc_v1_meetingRecording_start |
| vc.v1.meetingRecording.stop | vc_v1_meetingRecording_stop | lark-mcp__vc_v1_meetingRecording_stop |
| vc.v1.participantList.get | vc_v1_participantList_get | lark-mcp__vc_v1_participantList_get |
| vc.v1.participantQualityList.get | vc_v1_participantQualityList_get | lark-mcp__vc_v1_participantQualityList_get |
| vc.v1.report.getDaily | vc_v1_report_getDaily | lark-mcp__vc_v1_report_getDaily |
| vc.v1.report.getTopUser | vc_v1_report_getTopUser | lark-mcp__vc_v1_report_getTopUser |
| vc.v1.reserve.apply | vc_v1_reserve_apply | lark-mcp__vc_v1_reserve_apply |
| vc.v1.reserve.delete | vc_v1_reserve_delete | lark-mcp__vc_v1_reserve_delete |
| vc.v1.reserve.get | vc_v1_reserve_get | lark-mcp__vc_v1_reserve_get |
| vc.v1.reserve.getActiveMeeting | vc_v1_reserve_getActiveMeeting | lark-mcp__vc_v1_reserve_getActiveMeeting |
| vc.v1.reserve.update | vc_v1_reserve_update | lark-mcp__vc_v1_reserve_update |
| vc.v1.reserveConfig.patch | vc_v1_reserveConfig_patch | lark-mcp__vc_v1_reserveConfig_patch |
| vc.v1.reserveConfig.reserveScope | vc_v1_reserveConfig_reserveScope | lark-mcp__vc_v1_reserveConfig_reserveScope |
| vc.v1.reserveConfigAdmin.get | vc_v1_reserveConfigAdmin_get | lark-mcp__vc_v1_reserveConfigAdmin_get |
| vc.v1.reserveConfigAdmin.patch | vc_v1_reserveConfigAdmin_patch | lark-mcp__vc_v1_reserveConfigAdmin_patch |
| vc.v1.reserveConfigDisableInform.get | vc_v1_reserveConfigDisableInform_get | lark-mcp__vc_v1_reserveConfigDisableInform_get |
| vc.v1.reserveConfigDisableInform.patch | vc_v1_reserveConfigDisableInform_patch | lark-mcp__vc_v1_reserveConfigDisableInform_patch |
| vc.v1.reserveConfigForm.get | vc_v1_reserveConfigForm_get | lark-mcp__vc_v1_reserveConfigForm_get |
| vc.v1.reserveConfigForm.patch | vc_v1_reserveConfigForm_patch | lark-mcp__vc_v1_reserveConfigForm_patch |
| vc.v1.resourceReservationList.get | vc_v1_resourceReservationList_get | lark-mcp__vc_v1_resourceReservationList_get |
| vc.v1.room.create | vc_v1_room_create | lark-mcp__vc_v1_room_create |
| vc.v1.room.delete | vc_v1_room_delete | lark-mcp__vc_v1_room_delete |
| vc.v1.room.get | vc_v1_room_get | lark-mcp__vc_v1_room_get |
| vc.v1.room.list | vc_v1_room_list | lark-mcp__vc_v1_room_list |
| vc.v1.room.mget | vc_v1_room_mget | lark-mcp__vc_v1_room_mget |
| vc.v1.room.patch | vc_v1_room_patch | lark-mcp__vc_v1_room_patch |
| vc.v1.room.search | vc_v1_room_search | lark-mcp__vc_v1_room_search |
| vc.v1.roomConfig.query | vc_v1_roomConfig_query | lark-mcp__vc_v1_roomConfig_query |
| vc.v1.roomConfig.set | vc_v1_roomConfig_set | lark-mcp__vc_v1_roomConfig_set |
| vc.v1.roomConfig.setCheckboardAccessCode | vc_v1_roomConfig_setCheckboardAccessCode | lark-mcp__vc_v1_roomConfig_setCheckboardAccessCode |
| vc.v1.roomConfig.setRoomAccessCode | vc_v1_roomConfig_setRoomAccessCode | lark-mcp__vc_v1_roomConfig_setRoomAccessCode |
| vc.v1.roomLevel.create | vc_v1_roomLevel_create | lark-mcp__vc_v1_roomLevel_create |
| vc.v1.roomLevel.del | vc_v1_roomLevel_del | lark-mcp__vc_v1_roomLevel_del |
| vc.v1.roomLevel.get | vc_v1_roomLevel_get | lark-mcp__vc_v1_roomLevel_get |
| vc.v1.roomLevel.list | vc_v1_roomLevel_list | lark-mcp__vc_v1_roomLevel_list |
| vc.v1.roomLevel.mget | vc_v1_roomLevel_mget | lark-mcp__vc_v1_roomLevel_mget |
| vc.v1.roomLevel.patch | vc_v1_roomLevel_patch | lark-mcp__vc_v1_roomLevel_patch |
| vc.v1.roomLevel.search | vc_v1_roomLevel_search | lark-mcp__vc_v1_roomLevel_search |
| vc.v1.scopeConfig.create | vc_v1_scopeConfig_create | lark-mcp__vc_v1_scopeConfig_create |
| vc.v1.scopeConfig.get | vc_v1_scopeConfig_get | lark-mcp__vc_v1_scopeConfig_get |

## verificationV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| verification.v1.verification.get | verification_v1_verification_get | lark-mcp__verification_v1_verification_get |

## wikiV1 (1)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| wiki.v1.node.search | wiki_v1_node_search | lark-mcp__wiki_v1_node_search |

## wikiV2 (15)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| wiki.v2.space.create | wiki_v2_space_create | lark-mcp__wiki_v2_space_create |
| wiki.v2.space.get | wiki_v2_space_get | lark-mcp__wiki_v2_space_get |
| wiki.v2.space.getNode | wiki_v2_space_getNode | lark-mcp__wiki_v2_space_getNode |
| wiki.v2.space.list | wiki_v2_space_list | lark-mcp__wiki_v2_space_list |
| wiki.v2.spaceMember.create | wiki_v2_spaceMember_create | lark-mcp__wiki_v2_spaceMember_create |
| wiki.v2.spaceMember.delete | wiki_v2_spaceMember_delete | lark-mcp__wiki_v2_spaceMember_delete |
| wiki.v2.spaceMember.list | wiki_v2_spaceMember_list | lark-mcp__wiki_v2_spaceMember_list |
| wiki.v2.spaceNode.copy | wiki_v2_spaceNode_copy | lark-mcp__wiki_v2_spaceNode_copy |
| wiki.v2.spaceNode.create | wiki_v2_spaceNode_create | lark-mcp__wiki_v2_spaceNode_create |
| wiki.v2.spaceNode.list | wiki_v2_spaceNode_list | lark-mcp__wiki_v2_spaceNode_list |
| wiki.v2.spaceNode.move | wiki_v2_spaceNode_move | lark-mcp__wiki_v2_spaceNode_move |
| wiki.v2.spaceNode.moveDocsToWiki | wiki_v2_spaceNode_moveDocsToWiki | lark-mcp__wiki_v2_spaceNode_moveDocsToWiki |
| wiki.v2.spaceNode.updateTitle | wiki_v2_spaceNode_updateTitle | lark-mcp__wiki_v2_spaceNode_updateTitle |
| wiki.v2.spaceSetting.update | wiki_v2_spaceSetting_update | lark-mcp__wiki_v2_spaceSetting_update |
| wiki.v2.task.get | wiki_v2_task_get | lark-mcp__wiki_v2_task_get |

## workplaceV1 (3)

| OpenAPI（-t） | snake | OpenClaw 全名 |
|---|---|---|
| workplace.v1.customWorkplaceAccessData.search | workplace_v1_customWorkplaceAccessData_search | lark-mcp__workplace_v1_customWorkplaceAccessData_search |
| workplace.v1.workplaceAccessData.search | workplace_v1_workplaceAccessData_search | lark-mcp__workplace_v1_workplaceAccessData_search |
| workplace.v1.workplaceBlockAccessData.search | workplace_v1_workplaceBlockAccessData_search | lark-mcp__workplace_v1_workplaceBlockAccessData_search |

## 全量字母序索引

- acs.v1.accessRecord.list → lark-mcp__acs_v1_accessRecord_list
- acs.v1.device.list → lark-mcp__acs_v1_device_list
- acs.v1.ruleExternal.create → lark-mcp__acs_v1_ruleExternal_create
- acs.v1.ruleExternal.delete → lark-mcp__acs_v1_ruleExternal_delete
- acs.v1.ruleExternal.deviceBind → lark-mcp__acs_v1_ruleExternal_deviceBind
- acs.v1.ruleExternal.get → lark-mcp__acs_v1_ruleExternal_get
- acs.v1.user.get → lark-mcp__acs_v1_user_get
- acs.v1.user.list → lark-mcp__acs_v1_user_list
- acs.v1.user.patch → lark-mcp__acs_v1_user_patch
- acs.v1.visitor.create → lark-mcp__acs_v1_visitor_create
- acs.v1.visitor.delete → lark-mcp__acs_v1_visitor_delete
- admin.v1.adminDeptStat.list → lark-mcp__admin_v1_adminDeptStat_list
- admin.v1.adminUserStat.list → lark-mcp__admin_v1_adminUserStat_list
- admin.v1.auditInfo.list → lark-mcp__admin_v1_auditInfo_list
- admin.v1.badge.create → lark-mcp__admin_v1_badge_create
- admin.v1.badge.get → lark-mcp__admin_v1_badge_get
- admin.v1.badge.list → lark-mcp__admin_v1_badge_list
- admin.v1.badge.update → lark-mcp__admin_v1_badge_update
- admin.v1.badgeGrant.create → lark-mcp__admin_v1_badgeGrant_create
- admin.v1.badgeGrant.delete → lark-mcp__admin_v1_badgeGrant_delete
- admin.v1.badgeGrant.get → lark-mcp__admin_v1_badgeGrant_get
- admin.v1.badgeGrant.list → lark-mcp__admin_v1_badgeGrant_list
- admin.v1.badgeGrant.update → lark-mcp__admin_v1_badgeGrant_update
- admin.v1.password.reset → lark-mcp__admin_v1_password_reset
- aily.v1.ailySession.create → lark-mcp__aily_v1_ailySession_create
- aily.v1.ailySession.delete → lark-mcp__aily_v1_ailySession_delete
- aily.v1.ailySession.get → lark-mcp__aily_v1_ailySession_get
- aily.v1.ailySession.update → lark-mcp__aily_v1_ailySession_update
- aily.v1.ailySessionAilyMessage.create → lark-mcp__aily_v1_ailySessionAilyMessage_create
- aily.v1.ailySessionAilyMessage.get → lark-mcp__aily_v1_ailySessionAilyMessage_get
- aily.v1.ailySessionAilyMessage.list → lark-mcp__aily_v1_ailySessionAilyMessage_list
- aily.v1.ailySessionRun.cancel → lark-mcp__aily_v1_ailySessionRun_cancel
- aily.v1.ailySessionRun.create → lark-mcp__aily_v1_ailySessionRun_create
- aily.v1.ailySessionRun.get → lark-mcp__aily_v1_ailySessionRun_get
- aily.v1.ailySessionRun.list → lark-mcp__aily_v1_ailySessionRun_list
- aily.v1.appDataAsset.create → lark-mcp__aily_v1_appDataAsset_create
- aily.v1.appDataAsset.delete → lark-mcp__aily_v1_appDataAsset_delete
- aily.v1.appDataAsset.get → lark-mcp__aily_v1_appDataAsset_get
- aily.v1.appDataAsset.list → lark-mcp__aily_v1_appDataAsset_list
- aily.v1.appDataAssetTag.list → lark-mcp__aily_v1_appDataAssetTag_list
- aily.v1.appSkill.get → lark-mcp__aily_v1_appSkill_get
- aily.v1.appSkill.list → lark-mcp__aily_v1_appSkill_list
- aily.v1.appSkill.start → lark-mcp__aily_v1_appSkill_start
- apaas.v1.app.list → lark-mcp__apaas_v1_app_list
- apaas.v1.applicationAuditLog.auditLogList → lark-mcp__apaas_v1_applicationAuditLog_auditLogList
- apaas.v1.applicationAuditLog.dataChangeLogDetail → lark-mcp__apaas_v1_applicationAuditLog_dataChangeLogDetail
- apaas.v1.applicationAuditLog.dataChangeLogsList → lark-mcp__apaas_v1_applicationAuditLog_dataChangeLogsList
- apaas.v1.applicationAuditLog.get → lark-mcp__apaas_v1_applicationAuditLog_get
- apaas.v1.applicationEnvironmentVariable.get → lark-mcp__apaas_v1_applicationEnvironmentVariable_get
- apaas.v1.applicationEnvironmentVariable.query → lark-mcp__apaas_v1_applicationEnvironmentVariable_query
- apaas.v1.applicationFlow.execute → lark-mcp__apaas_v1_applicationFlow_execute
- apaas.v1.applicationFunction.invoke → lark-mcp__apaas_v1_applicationFunction_invoke
- apaas.v1.applicationObject.oqlQuery → lark-mcp__apaas_v1_applicationObject_oqlQuery
- apaas.v1.applicationObject.search → lark-mcp__apaas_v1_applicationObject_search
- apaas.v1.applicationObjectRecord.batchCreate → lark-mcp__apaas_v1_applicationObjectRecord_batchCreate
- apaas.v1.applicationObjectRecord.batchDelete → lark-mcp__apaas_v1_applicationObjectRecord_batchDelete
- apaas.v1.applicationObjectRecord.batchQuery → lark-mcp__apaas_v1_applicationObjectRecord_batchQuery
- apaas.v1.applicationObjectRecord.batchUpdate → lark-mcp__apaas_v1_applicationObjectRecord_batchUpdate
- apaas.v1.applicationObjectRecord.create → lark-mcp__apaas_v1_applicationObjectRecord_create
- apaas.v1.applicationObjectRecord.delete → lark-mcp__apaas_v1_applicationObjectRecord_delete
- apaas.v1.applicationObjectRecord.patch → lark-mcp__apaas_v1_applicationObjectRecord_patch
- apaas.v1.applicationObjectRecord.query → lark-mcp__apaas_v1_applicationObjectRecord_query
- apaas.v1.applicationRecordPermissionMember.batchCreateAuthorization → lark-mcp__apaas_v1_applicationRecordPermissionMember_batchCreateAuthorization
- apaas.v1.applicationRecordPermissionMember.batchRemoveAuthorization → lark-mcp__apaas_v1_applicationRecordPermissionMember_batchRemoveAuthorization
- apaas.v1.applicationRoleMember.batchCreateAuthorization → lark-mcp__apaas_v1_applicationRoleMember_batchCreateAuthorization
- apaas.v1.applicationRoleMember.batchRemoveAuthorization → lark-mcp__apaas_v1_applicationRoleMember_batchRemoveAuthorization
- apaas.v1.applicationRoleMember.get → lark-mcp__apaas_v1_applicationRoleMember_get
- apaas.v1.approvalInstance.cancel → lark-mcp__apaas_v1_approvalInstance_cancel
- apaas.v1.approvalTask.addAssignee → lark-mcp__apaas_v1_approvalTask_addAssignee
- apaas.v1.approvalTask.agree → lark-mcp__apaas_v1_approvalTask_agree
- apaas.v1.approvalTask.reject → lark-mcp__apaas_v1_approvalTask_reject
- apaas.v1.approvalTask.transfer → lark-mcp__apaas_v1_approvalTask_transfer
- apaas.v1.seatActivity.list → lark-mcp__apaas_v1_seatActivity_list
- apaas.v1.seatAssignment.list → lark-mcp__apaas_v1_seatAssignment_list
- apaas.v1.userTask.cc → lark-mcp__apaas_v1_userTask_cc
- apaas.v1.userTask.chatGroup → lark-mcp__apaas_v1_userTask_chatGroup
- apaas.v1.userTask.expediting → lark-mcp__apaas_v1_userTask_expediting
- apaas.v1.userTask.query → lark-mcp__apaas_v1_userTask_query
- apaas.v1.userTask.rollback → lark-mcp__apaas_v1_userTask_rollback
- apaas.v1.userTask.rollbackPoints → lark-mcp__apaas_v1_userTask_rollbackPoints
- application.v5.application.favourite → lark-mcp__application_v5_application_favourite
- application.v5.application.recommend → lark-mcp__application_v5_application_recommend
- application.v6.appBadge.set → lark-mcp__application_v6_appBadge_set
- application.v6.application.contactsRangeConfiguration → lark-mcp__application_v6_application_contactsRangeConfiguration
- application.v6.application.get → lark-mcp__application_v6_application_get
- application.v6.application.list → lark-mcp__application_v6_application_list
- application.v6.application.patch → lark-mcp__application_v6_application_patch
- application.v6.application.underauditlist → lark-mcp__application_v6_application_underauditlist
- application.v6.applicationAppUsage.departmentOverview → lark-mcp__application_v6_applicationAppUsage_departmentOverview
- application.v6.applicationAppUsage.messagePushOverview → lark-mcp__application_v6_applicationAppUsage_messagePushOverview
- application.v6.applicationAppUsage.overview → lark-mcp__application_v6_applicationAppUsage_overview
- application.v6.applicationAppVersion.contactsRangeSuggest → lark-mcp__application_v6_applicationAppVersion_contactsRangeSuggest
- application.v6.applicationAppVersion.get → lark-mcp__application_v6_applicationAppVersion_get
- application.v6.applicationAppVersion.list → lark-mcp__application_v6_applicationAppVersion_list
- application.v6.applicationAppVersion.patch → lark-mcp__application_v6_applicationAppVersion_patch
- application.v6.applicationCollaborators.get → lark-mcp__application_v6_applicationCollaborators_get
- application.v6.applicationCollaborators.update → lark-mcp__application_v6_applicationCollaborators_update
- application.v6.applicationContactsRange.patch → lark-mcp__application_v6_applicationContactsRange_patch
- application.v6.applicationFeedback.list → lark-mcp__application_v6_applicationFeedback_list
- application.v6.applicationFeedback.patch → lark-mcp__application_v6_applicationFeedback_patch
- application.v6.applicationManagement.update → lark-mcp__application_v6_applicationManagement_update
- application.v6.applicationOwner.update → lark-mcp__application_v6_applicationOwner_update
- application.v6.applicationVisibility.checkWhiteBlackList → lark-mcp__application_v6_applicationVisibility_checkWhiteBlackList
- application.v6.applicationVisibility.patch → lark-mcp__application_v6_applicationVisibility_patch
- application.v6.appRecommendRule.list → lark-mcp__application_v6_appRecommendRule_list
- application.v6.scope.apply → lark-mcp__application_v6_scope_apply
- application.v6.scope.list → lark-mcp__application_v6_scope_list
- approval.v4.approval.create → lark-mcp__approval_v4_approval_create
- approval.v4.approval.get → lark-mcp__approval_v4_approval_get
- approval.v4.approval.subscribe → lark-mcp__approval_v4_approval_subscribe
- approval.v4.approval.unsubscribe → lark-mcp__approval_v4_approval_unsubscribe
- approval.v4.externalApproval.create → lark-mcp__approval_v4_externalApproval_create
- approval.v4.externalApproval.get → lark-mcp__approval_v4_externalApproval_get
- approval.v4.externalInstance.check → lark-mcp__approval_v4_externalInstance_check
- approval.v4.externalInstance.create → lark-mcp__approval_v4_externalInstance_create
- approval.v4.externalTask.list → lark-mcp__approval_v4_externalTask_list
- approval.v4.instance.addSign → lark-mcp__approval_v4_instance_addSign
- approval.v4.instance.cancel → lark-mcp__approval_v4_instance_cancel
- approval.v4.instance.cc → lark-mcp__approval_v4_instance_cc
- approval.v4.instance.create → lark-mcp__approval_v4_instance_create
- approval.v4.instance.get → lark-mcp__approval_v4_instance_get
- approval.v4.instance.list → lark-mcp__approval_v4_instance_list
- approval.v4.instance.preview → lark-mcp__approval_v4_instance_preview
- approval.v4.instance.query → lark-mcp__approval_v4_instance_query
- approval.v4.instance.searchCc → lark-mcp__approval_v4_instance_searchCc
- approval.v4.instance.specifiedRollback → lark-mcp__approval_v4_instance_specifiedRollback
- approval.v4.instanceComment.create → lark-mcp__approval_v4_instanceComment_create
- approval.v4.instanceComment.delete → lark-mcp__approval_v4_instanceComment_delete
- approval.v4.instanceComment.list → lark-mcp__approval_v4_instanceComment_list
- approval.v4.instanceComment.remove → lark-mcp__approval_v4_instanceComment_remove
- approval.v4.task.approve → lark-mcp__approval_v4_task_approve
- approval.v4.task.query → lark-mcp__approval_v4_task_query
- approval.v4.task.reject → lark-mcp__approval_v4_task_reject
- approval.v4.task.resubmit → lark-mcp__approval_v4_task_resubmit
- approval.v4.task.search → lark-mcp__approval_v4_task_search
- approval.v4.task.transfer → lark-mcp__approval_v4_task_transfer
- attendance.v1.approvalInfo.process → lark-mcp__attendance_v1_approvalInfo_process
- attendance.v1.archiveRule.delReport → lark-mcp__attendance_v1_archiveRule_delReport
- attendance.v1.archiveRule.list → lark-mcp__attendance_v1_archiveRule_list
- attendance.v1.archiveRule.uploadReport → lark-mcp__attendance_v1_archiveRule_uploadReport
- attendance.v1.archiveRule.userStatsFieldsQuery → lark-mcp__attendance_v1_archiveRule_userStatsFieldsQuery
- attendance.v1.group.create → lark-mcp__attendance_v1_group_create
- attendance.v1.group.delete → lark-mcp__attendance_v1_group_delete
- attendance.v1.group.get → lark-mcp__attendance_v1_group_get
- attendance.v1.group.list → lark-mcp__attendance_v1_group_list
- attendance.v1.group.listUser → lark-mcp__attendance_v1_group_listUser
- attendance.v1.group.search → lark-mcp__attendance_v1_group_search
- attendance.v1.leaveAccrualRecord.patch → lark-mcp__attendance_v1_leaveAccrualRecord_patch
- attendance.v1.leaveEmployExpireRecord.get → lark-mcp__attendance_v1_leaveEmployExpireRecord_get
- attendance.v1.shift.create → lark-mcp__attendance_v1_shift_create
- attendance.v1.shift.delete → lark-mcp__attendance_v1_shift_delete
- attendance.v1.shift.get → lark-mcp__attendance_v1_shift_get
- attendance.v1.shift.list → lark-mcp__attendance_v1_shift_list
- attendance.v1.shift.query → lark-mcp__attendance_v1_shift_query
- attendance.v1.userApproval.create → lark-mcp__attendance_v1_userApproval_create
- attendance.v1.userApproval.query → lark-mcp__attendance_v1_userApproval_query
- attendance.v1.userDailyShift.batchCreate → lark-mcp__attendance_v1_userDailyShift_batchCreate
- attendance.v1.userDailyShift.batchCreateTemp → lark-mcp__attendance_v1_userDailyShift_batchCreateTemp
- attendance.v1.userDailyShift.query → lark-mcp__attendance_v1_userDailyShift_query
- attendance.v1.userFlow.batchCreate → lark-mcp__attendance_v1_userFlow_batchCreate
- attendance.v1.userFlow.batchDel → lark-mcp__attendance_v1_userFlow_batchDel
- attendance.v1.userFlow.get → lark-mcp__attendance_v1_userFlow_get
- attendance.v1.userFlow.query → lark-mcp__attendance_v1_userFlow_query
- attendance.v1.userSetting.modify → lark-mcp__attendance_v1_userSetting_modify
- attendance.v1.userSetting.query → lark-mcp__attendance_v1_userSetting_query
- attendance.v1.userStatsData.query → lark-mcp__attendance_v1_userStatsData_query
- attendance.v1.userStatsField.query → lark-mcp__attendance_v1_userStatsField_query
- attendance.v1.userStatsView.query → lark-mcp__attendance_v1_userStatsView_query
- attendance.v1.userStatsView.update → lark-mcp__attendance_v1_userStatsView_update
- attendance.v1.userTask.query → lark-mcp__attendance_v1_userTask_query
- attendance.v1.userTaskRemedy.create → lark-mcp__attendance_v1_userTaskRemedy_create
- attendance.v1.userTaskRemedy.query → lark-mcp__attendance_v1_userTaskRemedy_query
- attendance.v1.userTaskRemedy.queryUserAllowedRemedys → lark-mcp__attendance_v1_userTaskRemedy_queryUserAllowedRemedys
- auth.v3.auth.appAccessToken → lark-mcp__auth_v3_auth_appAccessToken
- auth.v3.auth.appAccessTokenInternal → lark-mcp__auth_v3_auth_appAccessTokenInternal
- auth.v3.auth.appTicketResend → lark-mcp__auth_v3_auth_appTicketResend
- auth.v3.auth.tenantAccessToken → lark-mcp__auth_v3_auth_tenantAccessToken
- auth.v3.auth.tenantAccessTokenInternal → lark-mcp__auth_v3_auth_tenantAccessTokenInternal
- authen.v1.userInfo.get → lark-mcp__authen_v1_userInfo_get
- baike.v1.classification.list → lark-mcp__baike_v1_classification_list
- baike.v1.draft.create → lark-mcp__baike_v1_draft_create
- baike.v1.draft.update → lark-mcp__baike_v1_draft_update
- baike.v1.entity.create → lark-mcp__baike_v1_entity_create
- baike.v1.entity.extract → lark-mcp__baike_v1_entity_extract
- baike.v1.entity.get → lark-mcp__baike_v1_entity_get
- baike.v1.entity.highlight → lark-mcp__baike_v1_entity_highlight
- baike.v1.entity.list → lark-mcp__baike_v1_entity_list
- baike.v1.entity.match → lark-mcp__baike_v1_entity_match
- baike.v1.entity.search → lark-mcp__baike_v1_entity_search
- baike.v1.entity.update → lark-mcp__baike_v1_entity_update
- base.v2.appRole.create → lark-mcp__base_v2_appRole_create
- base.v2.appRole.list → lark-mcp__base_v2_appRole_list
- base.v2.appRole.update → lark-mcp__base_v2_appRole_update
- bitable.v1.app.copy → lark-mcp__bitable_v1_app_copy
- bitable.v1.app.create → lark-mcp__bitable_v1_app_create
- bitable.v1.app.get → lark-mcp__bitable_v1_app_get
- bitable.v1.app.update → lark-mcp__bitable_v1_app_update
- bitable.v1.appDashboard.copy → lark-mcp__bitable_v1_appDashboard_copy
- bitable.v1.appDashboard.list → lark-mcp__bitable_v1_appDashboard_list
- bitable.v1.appRole.create → lark-mcp__bitable_v1_appRole_create
- bitable.v1.appRole.delete → lark-mcp__bitable_v1_appRole_delete
- bitable.v1.appRole.list → lark-mcp__bitable_v1_appRole_list
- bitable.v1.appRole.update → lark-mcp__bitable_v1_appRole_update
- bitable.v1.appRoleMember.batchCreate → lark-mcp__bitable_v1_appRoleMember_batchCreate
- bitable.v1.appRoleMember.batchDelete → lark-mcp__bitable_v1_appRoleMember_batchDelete
- bitable.v1.appRoleMember.create → lark-mcp__bitable_v1_appRoleMember_create
- bitable.v1.appRoleMember.delete → lark-mcp__bitable_v1_appRoleMember_delete
- bitable.v1.appRoleMember.list → lark-mcp__bitable_v1_appRoleMember_list
- bitable.v1.appTable.batchCreate → lark-mcp__bitable_v1_appTable_batchCreate
- bitable.v1.appTable.batchDelete → lark-mcp__bitable_v1_appTable_batchDelete
- bitable.v1.appTable.create → lark-mcp__bitable_v1_appTable_create
- bitable.v1.appTable.delete → lark-mcp__bitable_v1_appTable_delete
- bitable.v1.appTable.list → lark-mcp__bitable_v1_appTable_list
- bitable.v1.appTable.patch → lark-mcp__bitable_v1_appTable_patch
- bitable.v1.appTableField.create → lark-mcp__bitable_v1_appTableField_create
- bitable.v1.appTableField.delete → lark-mcp__bitable_v1_appTableField_delete
- bitable.v1.appTableField.list → lark-mcp__bitable_v1_appTableField_list
- bitable.v1.appTableField.update → lark-mcp__bitable_v1_appTableField_update
- bitable.v1.appTableForm.get → lark-mcp__bitable_v1_appTableForm_get
- bitable.v1.appTableForm.patch → lark-mcp__bitable_v1_appTableForm_patch
- bitable.v1.appTableFormField.list → lark-mcp__bitable_v1_appTableFormField_list
- bitable.v1.appTableFormField.patch → lark-mcp__bitable_v1_appTableFormField_patch
- bitable.v1.appTableRecord.batchCreate → lark-mcp__bitable_v1_appTableRecord_batchCreate
- bitable.v1.appTableRecord.batchDelete → lark-mcp__bitable_v1_appTableRecord_batchDelete
- bitable.v1.appTableRecord.batchGet → lark-mcp__bitable_v1_appTableRecord_batchGet
- bitable.v1.appTableRecord.batchUpdate → lark-mcp__bitable_v1_appTableRecord_batchUpdate
- bitable.v1.appTableRecord.create → lark-mcp__bitable_v1_appTableRecord_create
- bitable.v1.appTableRecord.delete → lark-mcp__bitable_v1_appTableRecord_delete
- bitable.v1.appTableRecord.get → lark-mcp__bitable_v1_appTableRecord_get
- bitable.v1.appTableRecord.list → lark-mcp__bitable_v1_appTableRecord_list
- bitable.v1.appTableRecord.search → lark-mcp__bitable_v1_appTableRecord_search
- bitable.v1.appTableRecord.update → lark-mcp__bitable_v1_appTableRecord_update
- bitable.v1.appTableView.create → lark-mcp__bitable_v1_appTableView_create
- bitable.v1.appTableView.delete → lark-mcp__bitable_v1_appTableView_delete
- bitable.v1.appTableView.get → lark-mcp__bitable_v1_appTableView_get
- bitable.v1.appTableView.list → lark-mcp__bitable_v1_appTableView_list
- bitable.v1.appTableView.patch → lark-mcp__bitable_v1_appTableView_patch
- bitable.v1.appWorkflow.list → lark-mcp__bitable_v1_appWorkflow_list
- bitable.v1.appWorkflow.update → lark-mcp__bitable_v1_appWorkflow_update
- board.v1.whiteboardNode.list → lark-mcp__board_v1_whiteboardNode_list
- calendar.v4.calendar.create → lark-mcp__calendar_v4_calendar_create
- calendar.v4.calendar.delete → lark-mcp__calendar_v4_calendar_delete
- calendar.v4.calendar.get → lark-mcp__calendar_v4_calendar_get
- calendar.v4.calendar.list → lark-mcp__calendar_v4_calendar_list
- calendar.v4.calendar.patch → lark-mcp__calendar_v4_calendar_patch
- calendar.v4.calendar.primary → lark-mcp__calendar_v4_calendar_primary
- calendar.v4.calendar.search → lark-mcp__calendar_v4_calendar_search
- calendar.v4.calendar.subscribe → lark-mcp__calendar_v4_calendar_subscribe
- calendar.v4.calendar.subscription → lark-mcp__calendar_v4_calendar_subscription
- calendar.v4.calendar.unsubscribe → lark-mcp__calendar_v4_calendar_unsubscribe
- calendar.v4.calendar.unsubscription → lark-mcp__calendar_v4_calendar_unsubscription
- calendar.v4.calendarAcl.create → lark-mcp__calendar_v4_calendarAcl_create
- calendar.v4.calendarAcl.delete → lark-mcp__calendar_v4_calendarAcl_delete
- calendar.v4.calendarAcl.list → lark-mcp__calendar_v4_calendarAcl_list
- calendar.v4.calendarAcl.subscription → lark-mcp__calendar_v4_calendarAcl_subscription
- calendar.v4.calendarAcl.unsubscription → lark-mcp__calendar_v4_calendarAcl_unsubscription
- calendar.v4.calendarEvent.create → lark-mcp__calendar_v4_calendarEvent_create
- calendar.v4.calendarEvent.delete → lark-mcp__calendar_v4_calendarEvent_delete
- calendar.v4.calendarEvent.get → lark-mcp__calendar_v4_calendarEvent_get
- calendar.v4.calendarEvent.instances → lark-mcp__calendar_v4_calendarEvent_instances
- calendar.v4.calendarEvent.instanceView → lark-mcp__calendar_v4_calendarEvent_instanceView
- calendar.v4.calendarEvent.list → lark-mcp__calendar_v4_calendarEvent_list
- calendar.v4.calendarEvent.patch → lark-mcp__calendar_v4_calendarEvent_patch
- calendar.v4.calendarEvent.reply → lark-mcp__calendar_v4_calendarEvent_reply
- calendar.v4.calendarEvent.search → lark-mcp__calendar_v4_calendarEvent_search
- calendar.v4.calendarEvent.subscription → lark-mcp__calendar_v4_calendarEvent_subscription
- calendar.v4.calendarEvent.unsubscription → lark-mcp__calendar_v4_calendarEvent_unsubscription
- calendar.v4.calendarEventAttendee.batchDelete → lark-mcp__calendar_v4_calendarEventAttendee_batchDelete
- calendar.v4.calendarEventAttendee.create → lark-mcp__calendar_v4_calendarEventAttendee_create
- calendar.v4.calendarEventAttendee.list → lark-mcp__calendar_v4_calendarEventAttendee_list
- calendar.v4.calendarEventAttendeeChatMember.list → lark-mcp__calendar_v4_calendarEventAttendeeChatMember_list
- calendar.v4.calendarEventMeetingChat.create → lark-mcp__calendar_v4_calendarEventMeetingChat_create
- calendar.v4.calendarEventMeetingChat.delete → lark-mcp__calendar_v4_calendarEventMeetingChat_delete
- calendar.v4.calendarEventMeetingMinute.create → lark-mcp__calendar_v4_calendarEventMeetingMinute_create
- calendar.v4.exchangeBinding.create → lark-mcp__calendar_v4_exchangeBinding_create
- calendar.v4.exchangeBinding.delete → lark-mcp__calendar_v4_exchangeBinding_delete
- calendar.v4.exchangeBinding.get → lark-mcp__calendar_v4_exchangeBinding_get
- calendar.v4.freebusy.list → lark-mcp__calendar_v4_freebusy_list
- calendar.v4.setting.generateCaldavConf → lark-mcp__calendar_v4_setting_generateCaldavConf
- calendar.v4.timeoffEvent.create → lark-mcp__calendar_v4_timeoffEvent_create
- calendar.v4.timeoffEvent.delete → lark-mcp__calendar_v4_timeoffEvent_delete
- cardkit.v1.card.batchUpdate → lark-mcp__cardkit_v1_card_batchUpdate
- cardkit.v1.card.create → lark-mcp__cardkit_v1_card_create
- cardkit.v1.card.idConvert → lark-mcp__cardkit_v1_card_idConvert
- cardkit.v1.card.settings → lark-mcp__cardkit_v1_card_settings
- cardkit.v1.card.update → lark-mcp__cardkit_v1_card_update
- cardkit.v1.cardElement.content → lark-mcp__cardkit_v1_cardElement_content
- cardkit.v1.cardElement.create → lark-mcp__cardkit_v1_cardElement_create
- cardkit.v1.cardElement.delete → lark-mcp__cardkit_v1_cardElement_delete
- cardkit.v1.cardElement.patch → lark-mcp__cardkit_v1_cardElement_patch
- cardkit.v1.cardElement.update → lark-mcp__cardkit_v1_cardElement_update
- compensation.v1.archive.query → lark-mcp__compensation_v1_archive_query
- compensation.v1.changeReason.list → lark-mcp__compensation_v1_changeReason_list
- compensation.v1.indicator.list → lark-mcp__compensation_v1_indicator_list
- compensation.v1.item.list → lark-mcp__compensation_v1_item_list
- compensation.v1.itemCategory.list → lark-mcp__compensation_v1_itemCategory_list
- compensation.v1.plan.list → lark-mcp__compensation_v1_plan_list
- contact.v3.customAttr.list → lark-mcp__contact_v3_customAttr_list
- contact.v3.department.batch → lark-mcp__contact_v3_department_batch
- contact.v3.department.children → lark-mcp__contact_v3_department_children
- contact.v3.department.create → lark-mcp__contact_v3_department_create
- contact.v3.department.delete → lark-mcp__contact_v3_department_delete
- contact.v3.department.get → lark-mcp__contact_v3_department_get
- contact.v3.department.list → lark-mcp__contact_v3_department_list
- contact.v3.department.parent → lark-mcp__contact_v3_department_parent
- contact.v3.department.patch → lark-mcp__contact_v3_department_patch
- contact.v3.department.search → lark-mcp__contact_v3_department_search
- contact.v3.department.unbindDepartmentChat → lark-mcp__contact_v3_department_unbindDepartmentChat
- contact.v3.department.update → lark-mcp__contact_v3_department_update
- contact.v3.department.updateDepartmentId → lark-mcp__contact_v3_department_updateDepartmentId
- contact.v3.employeeTypeEnum.create → lark-mcp__contact_v3_employeeTypeEnum_create
- contact.v3.employeeTypeEnum.delete → lark-mcp__contact_v3_employeeTypeEnum_delete
- contact.v3.employeeTypeEnum.list → lark-mcp__contact_v3_employeeTypeEnum_list
- contact.v3.employeeTypeEnum.update → lark-mcp__contact_v3_employeeTypeEnum_update
- contact.v3.functionalRole.create → lark-mcp__contact_v3_functionalRole_create
- contact.v3.functionalRole.delete → lark-mcp__contact_v3_functionalRole_delete
- contact.v3.functionalRole.update → lark-mcp__contact_v3_functionalRole_update
- contact.v3.functionalRoleMember.batchCreate → lark-mcp__contact_v3_functionalRoleMember_batchCreate
- contact.v3.functionalRoleMember.batchDelete → lark-mcp__contact_v3_functionalRoleMember_batchDelete
- contact.v3.functionalRoleMember.get → lark-mcp__contact_v3_functionalRoleMember_get
- contact.v3.functionalRoleMember.list → lark-mcp__contact_v3_functionalRoleMember_list
- contact.v3.functionalRoleMember.scopes → lark-mcp__contact_v3_functionalRoleMember_scopes
- contact.v3.group.create → lark-mcp__contact_v3_group_create
- contact.v3.group.delete → lark-mcp__contact_v3_group_delete
- contact.v3.group.get → lark-mcp__contact_v3_group_get
- contact.v3.group.memberBelong → lark-mcp__contact_v3_group_memberBelong
- contact.v3.group.patch → lark-mcp__contact_v3_group_patch
- contact.v3.group.simplelist → lark-mcp__contact_v3_group_simplelist
- contact.v3.groupMember.add → lark-mcp__contact_v3_groupMember_add
- contact.v3.groupMember.batchAdd → lark-mcp__contact_v3_groupMember_batchAdd
- contact.v3.groupMember.batchRemove → lark-mcp__contact_v3_groupMember_batchRemove
- contact.v3.groupMember.remove → lark-mcp__contact_v3_groupMember_remove
- contact.v3.groupMember.simplelist → lark-mcp__contact_v3_groupMember_simplelist
- contact.v3.jobFamily.create → lark-mcp__contact_v3_jobFamily_create
- contact.v3.jobFamily.delete → lark-mcp__contact_v3_jobFamily_delete
- contact.v3.jobFamily.get → lark-mcp__contact_v3_jobFamily_get
- contact.v3.jobFamily.list → lark-mcp__contact_v3_jobFamily_list
- contact.v3.jobFamily.update → lark-mcp__contact_v3_jobFamily_update
- contact.v3.jobLevel.create → lark-mcp__contact_v3_jobLevel_create
- contact.v3.jobLevel.delete → lark-mcp__contact_v3_jobLevel_delete
- contact.v3.jobLevel.get → lark-mcp__contact_v3_jobLevel_get
- contact.v3.jobLevel.list → lark-mcp__contact_v3_jobLevel_list
- contact.v3.jobLevel.update → lark-mcp__contact_v3_jobLevel_update
- contact.v3.jobTitle.get → lark-mcp__contact_v3_jobTitle_get
- contact.v3.jobTitle.list → lark-mcp__contact_v3_jobTitle_list
- contact.v3.scope.list → lark-mcp__contact_v3_scope_list
- contact.v3.unit.bindDepartment → lark-mcp__contact_v3_unit_bindDepartment
- contact.v3.unit.create → lark-mcp__contact_v3_unit_create
- contact.v3.unit.delete → lark-mcp__contact_v3_unit_delete
- contact.v3.unit.get → lark-mcp__contact_v3_unit_get
- contact.v3.unit.list → lark-mcp__contact_v3_unit_list
- contact.v3.unit.listDepartment → lark-mcp__contact_v3_unit_listDepartment
- contact.v3.unit.patch → lark-mcp__contact_v3_unit_patch
- contact.v3.unit.unbindDepartment → lark-mcp__contact_v3_unit_unbindDepartment
- contact.v3.user.batch → lark-mcp__contact_v3_user_batch
- contact.v3.user.batchGetId → lark-mcp__contact_v3_user_batchGetId
- contact.v3.user.create → lark-mcp__contact_v3_user_create
- contact.v3.user.delete → lark-mcp__contact_v3_user_delete
- contact.v3.user.findByDepartment → lark-mcp__contact_v3_user_findByDepartment
- contact.v3.user.get → lark-mcp__contact_v3_user_get
- contact.v3.user.list → lark-mcp__contact_v3_user_list
- contact.v3.user.patch → lark-mcp__contact_v3_user_patch
- contact.v3.user.resurrect → lark-mcp__contact_v3_user_resurrect
- contact.v3.user.update → lark-mcp__contact_v3_user_update
- contact.v3.user.updateUserId → lark-mcp__contact_v3_user_updateUserId
- contact.v3.workCity.get → lark-mcp__contact_v3_workCity_get
- contact.v3.workCity.list → lark-mcp__contact_v3_workCity_list
- corehr.v1.assignedUser.search → lark-mcp__corehr_v1_assignedUser_search
- corehr.v1.authorization.addRoleAssign → lark-mcp__corehr_v1_authorization_addRoleAssign
- corehr.v1.authorization.getByParam → lark-mcp__corehr_v1_authorization_getByParam
- corehr.v1.authorization.query → lark-mcp__corehr_v1_authorization_query
- corehr.v1.authorization.removeRoleAssign → lark-mcp__corehr_v1_authorization_removeRoleAssign
- corehr.v1.authorization.updateRoleAssign → lark-mcp__corehr_v1_authorization_updateRoleAssign
- corehr.v1.commonDataId.convert → lark-mcp__corehr_v1_commonDataId_convert
- corehr.v1.commonDataMetaData.addEnumOption → lark-mcp__corehr_v1_commonDataMetaData_addEnumOption
- corehr.v1.commonDataMetaData.editEnumOption → lark-mcp__corehr_v1_commonDataMetaData_editEnumOption
- corehr.v1.company.create → lark-mcp__corehr_v1_company_create
- corehr.v1.company.delete → lark-mcp__corehr_v1_company_delete
- corehr.v1.company.get → lark-mcp__corehr_v1_company_get
- corehr.v1.company.list → lark-mcp__corehr_v1_company_list
- corehr.v1.company.patch → lark-mcp__corehr_v1_company_patch
- corehr.v1.compensationStandard.match → lark-mcp__corehr_v1_compensationStandard_match
- corehr.v1.contract.create → lark-mcp__corehr_v1_contract_create
- corehr.v1.contract.delete → lark-mcp__corehr_v1_contract_delete
- corehr.v1.contract.get → lark-mcp__corehr_v1_contract_get
- corehr.v1.contract.list → lark-mcp__corehr_v1_contract_list
- corehr.v1.contract.patch → lark-mcp__corehr_v1_contract_patch
- corehr.v1.countryRegion.get → lark-mcp__corehr_v1_countryRegion_get
- corehr.v1.countryRegion.list → lark-mcp__corehr_v1_countryRegion_list
- corehr.v1.currency.get → lark-mcp__corehr_v1_currency_get
- corehr.v1.currency.list → lark-mcp__corehr_v1_currency_list
- corehr.v1.customField.getByParam → lark-mcp__corehr_v1_customField_getByParam
- corehr.v1.customField.listObjectApiName → lark-mcp__corehr_v1_customField_listObjectApiName
- corehr.v1.customField.query → lark-mcp__corehr_v1_customField_query
- corehr.v1.department.create → lark-mcp__corehr_v1_department_create
- corehr.v1.department.delete → lark-mcp__corehr_v1_department_delete
- corehr.v1.department.get → lark-mcp__corehr_v1_department_get
- corehr.v1.department.list → lark-mcp__corehr_v1_department_list
- corehr.v1.department.patch → lark-mcp__corehr_v1_department_patch
- corehr.v1.employeeType.create → lark-mcp__corehr_v1_employeeType_create
- corehr.v1.employeeType.delete → lark-mcp__corehr_v1_employeeType_delete
- corehr.v1.employeeType.get → lark-mcp__corehr_v1_employeeType_get
- corehr.v1.employeeType.list → lark-mcp__corehr_v1_employeeType_list
- corehr.v1.employeeType.patch → lark-mcp__corehr_v1_employeeType_patch
- corehr.v1.employment.create → lark-mcp__corehr_v1_employment_create
- corehr.v1.employment.delete → lark-mcp__corehr_v1_employment_delete
- corehr.v1.employment.patch → lark-mcp__corehr_v1_employment_patch
- corehr.v1.job.create → lark-mcp__corehr_v1_job_create
- corehr.v1.job.delete → lark-mcp__corehr_v1_job_delete
- corehr.v1.job.get → lark-mcp__corehr_v1_job_get
- corehr.v1.job.list → lark-mcp__corehr_v1_job_list
- corehr.v1.job.patch → lark-mcp__corehr_v1_job_patch
- corehr.v1.jobChange.create → lark-mcp__corehr_v1_jobChange_create
- corehr.v1.jobData.create → lark-mcp__corehr_v1_jobData_create
- corehr.v1.jobData.delete → lark-mcp__corehr_v1_jobData_delete
- corehr.v1.jobData.get → lark-mcp__corehr_v1_jobData_get
- corehr.v1.jobData.list → lark-mcp__corehr_v1_jobData_list
- corehr.v1.jobData.patch → lark-mcp__corehr_v1_jobData_patch
- corehr.v1.jobFamily.create → lark-mcp__corehr_v1_jobFamily_create
- corehr.v1.jobFamily.delete → lark-mcp__corehr_v1_jobFamily_delete
- corehr.v1.jobFamily.get → lark-mcp__corehr_v1_jobFamily_get
- corehr.v1.jobFamily.list → lark-mcp__corehr_v1_jobFamily_list
- corehr.v1.jobFamily.patch → lark-mcp__corehr_v1_jobFamily_patch
- corehr.v1.jobLevel.create → lark-mcp__corehr_v1_jobLevel_create
- corehr.v1.jobLevel.delete → lark-mcp__corehr_v1_jobLevel_delete
- corehr.v1.jobLevel.get → lark-mcp__corehr_v1_jobLevel_get
- corehr.v1.jobLevel.list → lark-mcp__corehr_v1_jobLevel_list
- corehr.v1.jobLevel.patch → lark-mcp__corehr_v1_jobLevel_patch
- corehr.v1.leave.calendarByScope → lark-mcp__corehr_v1_leave_calendarByScope
- corehr.v1.leave.leaveBalances → lark-mcp__corehr_v1_leave_leaveBalances
- corehr.v1.leave.leaveRequestHistory → lark-mcp__corehr_v1_leave_leaveRequestHistory
- corehr.v1.leave.leaveTypes → lark-mcp__corehr_v1_leave_leaveTypes
- corehr.v1.leave.workCalendar → lark-mcp__corehr_v1_leave_workCalendar
- corehr.v1.leave.workCalendarDate → lark-mcp__corehr_v1_leave_workCalendarDate
- corehr.v1.leaveGrantingRecord.create → lark-mcp__corehr_v1_leaveGrantingRecord_create
- corehr.v1.leaveGrantingRecord.delete → lark-mcp__corehr_v1_leaveGrantingRecord_delete
- corehr.v1.location.create → lark-mcp__corehr_v1_location_create
- corehr.v1.location.delete → lark-mcp__corehr_v1_location_delete
- corehr.v1.location.get → lark-mcp__corehr_v1_location_get
- corehr.v1.location.list → lark-mcp__corehr_v1_location_list
- corehr.v1.nationalIdType.create → lark-mcp__corehr_v1_nationalIdType_create
- corehr.v1.nationalIdType.delete → lark-mcp__corehr_v1_nationalIdType_delete
- corehr.v1.nationalIdType.get → lark-mcp__corehr_v1_nationalIdType_get
- corehr.v1.nationalIdType.list → lark-mcp__corehr_v1_nationalIdType_list
- corehr.v1.nationalIdType.patch → lark-mcp__corehr_v1_nationalIdType_patch
- corehr.v1.offboarding.query → lark-mcp__corehr_v1_offboarding_query
- corehr.v1.offboarding.search → lark-mcp__corehr_v1_offboarding_search
- corehr.v1.offboarding.submit → lark-mcp__corehr_v1_offboarding_submit
- corehr.v1.person.create → lark-mcp__corehr_v1_person_create
- corehr.v1.person.delete → lark-mcp__corehr_v1_person_delete
- corehr.v1.person.get → lark-mcp__corehr_v1_person_get
- corehr.v1.person.patch → lark-mcp__corehr_v1_person_patch
- corehr.v1.preHire.delete → lark-mcp__corehr_v1_preHire_delete
- corehr.v1.preHire.get → lark-mcp__corehr_v1_preHire_get
- corehr.v1.preHire.list → lark-mcp__corehr_v1_preHire_list
- corehr.v1.preHire.patch → lark-mcp__corehr_v1_preHire_patch
- corehr.v1.processFormVariableData.get → lark-mcp__corehr_v1_processFormVariableData_get
- corehr.v1.securityGroup.list → lark-mcp__corehr_v1_securityGroup_list
- corehr.v1.securityGroup.query → lark-mcp__corehr_v1_securityGroup_query
- corehr.v1.subdivision.get → lark-mcp__corehr_v1_subdivision_get
- corehr.v1.subdivision.list → lark-mcp__corehr_v1_subdivision_list
- corehr.v1.subregion.get → lark-mcp__corehr_v1_subregion_get
- corehr.v1.subregion.list → lark-mcp__corehr_v1_subregion_list
- corehr.v1.transferReason.query → lark-mcp__corehr_v1_transferReason_query
- corehr.v1.transferType.query → lark-mcp__corehr_v1_transferType_query
- corehr.v1.workingHoursType.create → lark-mcp__corehr_v1_workingHoursType_create
- corehr.v1.workingHoursType.delete → lark-mcp__corehr_v1_workingHoursType_delete
- corehr.v1.workingHoursType.get → lark-mcp__corehr_v1_workingHoursType_get
- corehr.v1.workingHoursType.list → lark-mcp__corehr_v1_workingHoursType_list
- corehr.v1.workingHoursType.patch → lark-mcp__corehr_v1_workingHoursType_patch
- corehr.v2.approvalGroups.get → lark-mcp__corehr_v2_approvalGroups_get
- corehr.v2.approvalGroups.openQueryDepartmentChangeListByIds → lark-mcp__corehr_v2_approvalGroups_openQueryDepartmentChangeListByIds
- corehr.v2.approvalGroups.openQueryJobChangeListByIds → lark-mcp__corehr_v2_approvalGroups_openQueryJobChangeListByIds
- corehr.v2.approver.list → lark-mcp__corehr_v2_approver_list
- corehr.v2.basicInfoBank.search → lark-mcp__corehr_v2_basicInfoBank_search
- corehr.v2.basicInfoBankBranch.search → lark-mcp__corehr_v2_basicInfoBankBranch_search
- corehr.v2.basicInfoCity.search → lark-mcp__corehr_v2_basicInfoCity_search
- corehr.v2.basicInfoCountryRegion.search → lark-mcp__corehr_v2_basicInfoCountryRegion_search
- corehr.v2.basicInfoCountryRegionSubdivision.search → lark-mcp__corehr_v2_basicInfoCountryRegionSubdivision_search
- corehr.v2.basicInfoCurrency.search → lark-mcp__corehr_v2_basicInfoCurrency_search
- corehr.v2.basicInfoDistrict.search → lark-mcp__corehr_v2_basicInfoDistrict_search
- corehr.v2.basicInfoLanguage.search → lark-mcp__corehr_v2_basicInfoLanguage_search
- corehr.v2.basicInfoNationality.search → lark-mcp__corehr_v2_basicInfoNationality_search
- corehr.v2.basicInfoTimeZone.search → lark-mcp__corehr_v2_basicInfoTimeZone_search
- corehr.v2.bp.getByDepartment → lark-mcp__corehr_v2_bp_getByDepartment
- corehr.v2.bp.list → lark-mcp__corehr_v2_bp_list
- corehr.v2.company.active → lark-mcp__corehr_v2_company_active
- corehr.v2.company.batchGet → lark-mcp__corehr_v2_company_batchGet
- corehr.v2.company.queryRecentChange → lark-mcp__corehr_v2_company_queryRecentChange
- corehr.v2.contract.search → lark-mcp__corehr_v2_contract_search
- corehr.v2.costAllocation.batchQuery → lark-mcp__corehr_v2_costAllocation_batchQuery
- corehr.v2.costAllocation.createVersion → lark-mcp__corehr_v2_costAllocation_createVersion
- corehr.v2.costAllocation.removeVersion → lark-mcp__corehr_v2_costAllocation_removeVersion
- corehr.v2.costAllocation.updateVersion → lark-mcp__corehr_v2_costAllocation_updateVersion
- corehr.v2.costCenter.create → lark-mcp__corehr_v2_costCenter_create
- corehr.v2.costCenter.delete → lark-mcp__corehr_v2_costCenter_delete
- corehr.v2.costCenter.patch → lark-mcp__corehr_v2_costCenter_patch
- corehr.v2.costCenter.queryRecentChange → lark-mcp__corehr_v2_costCenter_queryRecentChange
- corehr.v2.costCenter.search → lark-mcp__corehr_v2_costCenter_search
- corehr.v2.costCenterVersion.create → lark-mcp__corehr_v2_costCenterVersion_create
- corehr.v2.costCenterVersion.delete → lark-mcp__corehr_v2_costCenterVersion_delete
- corehr.v2.costCenterVersion.patch → lark-mcp__corehr_v2_costCenterVersion_patch
- corehr.v2.customOrg.active → lark-mcp__corehr_v2_customOrg_active
- corehr.v2.customOrg.create → lark-mcp__corehr_v2_customOrg_create
- corehr.v2.customOrg.deleteOrg → lark-mcp__corehr_v2_customOrg_deleteOrg
- corehr.v2.customOrg.patch → lark-mcp__corehr_v2_customOrg_patch
- corehr.v2.customOrg.query → lark-mcp__corehr_v2_customOrg_query
- corehr.v2.customOrg.updateRule → lark-mcp__corehr_v2_customOrg_updateRule
- corehr.v2.defaultCostCenter.batchQuery → lark-mcp__corehr_v2_defaultCostCenter_batchQuery
- corehr.v2.defaultCostCenter.createVersion → lark-mcp__corehr_v2_defaultCostCenter_createVersion
- corehr.v2.defaultCostCenter.removeVersion → lark-mcp__corehr_v2_defaultCostCenter_removeVersion
- corehr.v2.defaultCostCenter.updateVersion → lark-mcp__corehr_v2_defaultCostCenter_updateVersion
- corehr.v2.department.batchGet → lark-mcp__corehr_v2_department_batchGet
- corehr.v2.department.delete → lark-mcp__corehr_v2_department_delete
- corehr.v2.department.parents → lark-mcp__corehr_v2_department_parents
- corehr.v2.department.patch → lark-mcp__corehr_v2_department_patch
- corehr.v2.department.queryMultiTimeline → lark-mcp__corehr_v2_department_queryMultiTimeline
- corehr.v2.department.queryOperationLogs → lark-mcp__corehr_v2_department_queryOperationLogs
- corehr.v2.department.queryRecentChange → lark-mcp__corehr_v2_department_queryRecentChange
- corehr.v2.department.queryTimeline → lark-mcp__corehr_v2_department_queryTimeline
- corehr.v2.department.search → lark-mcp__corehr_v2_department_search
- corehr.v2.department.tree → lark-mcp__corehr_v2_department_tree
- corehr.v2.employee.batchGet → lark-mcp__corehr_v2_employee_batchGet
- corehr.v2.employee.create → lark-mcp__corehr_v2_employee_create
- corehr.v2.employee.search → lark-mcp__corehr_v2_employee_search
- corehr.v2.employeesAdditionalJob.batch → lark-mcp__corehr_v2_employeesAdditionalJob_batch
- corehr.v2.employeesAdditionalJob.create → lark-mcp__corehr_v2_employeesAdditionalJob_create
- corehr.v2.employeesAdditionalJob.delete → lark-mcp__corehr_v2_employeesAdditionalJob_delete
- corehr.v2.employeesAdditionalJob.patch → lark-mcp__corehr_v2_employeesAdditionalJob_patch
- corehr.v2.employeesBp.batchGet → lark-mcp__corehr_v2_employeesBp_batchGet
- corehr.v2.employeesInternationalAssignment.create → lark-mcp__corehr_v2_employeesInternationalAssignment_create
- corehr.v2.employeesInternationalAssignment.delete → lark-mcp__corehr_v2_employeesInternationalAssignment_delete
- corehr.v2.employeesInternationalAssignment.list → lark-mcp__corehr_v2_employeesInternationalAssignment_list
- corehr.v2.employeesInternationalAssignment.patch → lark-mcp__corehr_v2_employeesInternationalAssignment_patch
- corehr.v2.employeesJobData.batchGet → lark-mcp__corehr_v2_employeesJobData_batchGet
- corehr.v2.employeesJobData.query → lark-mcp__corehr_v2_employeesJobData_query
- corehr.v2.enum.search → lark-mcp__corehr_v2_enum_search
- corehr.v2.job.get → lark-mcp__corehr_v2_job_get
- corehr.v2.job.list → lark-mcp__corehr_v2_job_list
- corehr.v2.job.queryRecentChange → lark-mcp__corehr_v2_job_queryRecentChange
- corehr.v2.jobChange.create → lark-mcp__corehr_v2_jobChange_create
- corehr.v2.jobChange.revoke → lark-mcp__corehr_v2_jobChange_revoke
- corehr.v2.jobChange.search → lark-mcp__corehr_v2_jobChange_search
- corehr.v2.jobFamily.batchGet → lark-mcp__corehr_v2_jobFamily_batchGet
- corehr.v2.jobGrade.create → lark-mcp__corehr_v2_jobGrade_create
- corehr.v2.jobGrade.delete → lark-mcp__corehr_v2_jobGrade_delete
- corehr.v2.jobGrade.patch → lark-mcp__corehr_v2_jobGrade_patch
- corehr.v2.jobGrade.query → lark-mcp__corehr_v2_jobGrade_query
- corehr.v2.jobLevel.batchGet → lark-mcp__corehr_v2_jobLevel_batchGet
- corehr.v2.location.active → lark-mcp__corehr_v2_location_active
- corehr.v2.location.batchGet → lark-mcp__corehr_v2_location_batchGet
- corehr.v2.location.patch → lark-mcp__corehr_v2_location_patch
- corehr.v2.location.queryRecentChange → lark-mcp__corehr_v2_location_queryRecentChange
- corehr.v2.locationAddress.create → lark-mcp__corehr_v2_locationAddress_create
- corehr.v2.locationAddress.delete → lark-mcp__corehr_v2_locationAddress_delete
- corehr.v2.locationAddress.patch → lark-mcp__corehr_v2_locationAddress_patch
- corehr.v2.offboarding.edit → lark-mcp__corehr_v2_offboarding_edit
- corehr.v2.offboarding.revoke → lark-mcp__corehr_v2_offboarding_revoke
- corehr.v2.offboarding.submitV2 → lark-mcp__corehr_v2_offboarding_submitV2
- corehr.v2.person.create → lark-mcp__corehr_v2_person_create
- corehr.v2.person.patch → lark-mcp__corehr_v2_person_patch
- corehr.v2.preHire.complete → lark-mcp__corehr_v2_preHire_complete
- corehr.v2.preHire.create → lark-mcp__corehr_v2_preHire_create
- corehr.v2.preHire.delete → lark-mcp__corehr_v2_preHire_delete
- corehr.v2.preHire.patch → lark-mcp__corehr_v2_preHire_patch
- corehr.v2.preHire.query → lark-mcp__corehr_v2_preHire_query
- corehr.v2.preHire.restoreFlowInstance → lark-mcp__corehr_v2_preHire_restoreFlowInstance
- corehr.v2.preHire.search → lark-mcp__corehr_v2_preHire_search
- corehr.v2.preHire.transitTask → lark-mcp__corehr_v2_preHire_transitTask
- corehr.v2.preHire.withdrawOnboarding → lark-mcp__corehr_v2_preHire_withdrawOnboarding
- corehr.v2.process.get → lark-mcp__corehr_v2_process_get
- corehr.v2.process.list → lark-mcp__corehr_v2_process_list
- corehr.v2.processApprover.update → lark-mcp__corehr_v2_processApprover_update
- corehr.v2.processExtra.update → lark-mcp__corehr_v2_processExtra_update
- corehr.v2.processFormVariableData.get → lark-mcp__corehr_v2_processFormVariableData_get
- corehr.v2.processRevoke.update → lark-mcp__corehr_v2_processRevoke_update
- corehr.v2.processTransfer.update → lark-mcp__corehr_v2_processTransfer_update
- corehr.v2.processWithdraw.update → lark-mcp__corehr_v2_processWithdraw_update
- corehr.v2.reportDetailRow.batchDelete → lark-mcp__corehr_v2_reportDetailRow_batchDelete
- corehr.v2.reportDetailRow.batchSave → lark-mcp__corehr_v2_reportDetailRow_batchSave
- corehr.v2.workforcePlan.list → lark-mcp__corehr_v2_workforcePlan_list
- corehr.v2.workforcePlanDetail.batch → lark-mcp__corehr_v2_workforcePlanDetail_batch
- corehr.v2.workforcePlanDetail.batchV2 → lark-mcp__corehr_v2_workforcePlanDetail_batchV2
- corehr.v2.workforcePlanDetailRow.batchDelete → lark-mcp__corehr_v2_workforcePlanDetailRow_batchDelete
- corehr.v2.workforcePlanDetailRow.batchSave → lark-mcp__corehr_v2_workforcePlanDetailRow_batchSave
- directory.v1.collaborationRule.create → lark-mcp__directory_v1_collaborationRule_create
- directory.v1.collaborationRule.delete → lark-mcp__directory_v1_collaborationRule_delete
- directory.v1.collaborationRule.list → lark-mcp__directory_v1_collaborationRule_list
- directory.v1.collaborationRule.update → lark-mcp__directory_v1_collaborationRule_update
- directory.v1.collaborationTenant.list → lark-mcp__directory_v1_collaborationTenant_list
- directory.v1.collborationShareEntity.list → lark-mcp__directory_v1_collborationShareEntity_list
- directory.v1.department.create → lark-mcp__directory_v1_department_create
- directory.v1.department.delete → lark-mcp__directory_v1_department_delete
- directory.v1.department.filter → lark-mcp__directory_v1_department_filter
- directory.v1.department.mget → lark-mcp__directory_v1_department_mget
- directory.v1.department.patch → lark-mcp__directory_v1_department_patch
- directory.v1.department.search → lark-mcp__directory_v1_department_search
- directory.v1.employee.create → lark-mcp__directory_v1_employee_create
- directory.v1.employee.delete → lark-mcp__directory_v1_employee_delete
- directory.v1.employee.filter → lark-mcp__directory_v1_employee_filter
- directory.v1.employee.mget → lark-mcp__directory_v1_employee_mget
- directory.v1.employee.patch → lark-mcp__directory_v1_employee_patch
- directory.v1.employee.regular → lark-mcp__directory_v1_employee_regular
- directory.v1.employee.resurrect → lark-mcp__directory_v1_employee_resurrect
- directory.v1.employee.search → lark-mcp__directory_v1_employee_search
- directory.v1.employee.toBeResigned → lark-mcp__directory_v1_employee_toBeResigned
- docs.v1.content.get → lark-mcp__docs_v1_content_get
- docx.v1.chatAnnouncement.get → lark-mcp__docx_v1_chatAnnouncement_get
- docx.v1.chatAnnouncementBlock.batchUpdate → lark-mcp__docx_v1_chatAnnouncementBlock_batchUpdate
- docx.v1.chatAnnouncementBlock.get → lark-mcp__docx_v1_chatAnnouncementBlock_get
- docx.v1.chatAnnouncementBlock.list → lark-mcp__docx_v1_chatAnnouncementBlock_list
- docx.v1.chatAnnouncementBlockChildren.batchDelete → lark-mcp__docx_v1_chatAnnouncementBlockChildren_batchDelete
- docx.v1.chatAnnouncementBlockChildren.create → lark-mcp__docx_v1_chatAnnouncementBlockChildren_create
- docx.v1.chatAnnouncementBlockChildren.get → lark-mcp__docx_v1_chatAnnouncementBlockChildren_get
- docx.v1.document.convert → lark-mcp__docx_v1_document_convert
- docx.v1.document.create → lark-mcp__docx_v1_document_create
- docx.v1.document.get → lark-mcp__docx_v1_document_get
- docx.v1.document.rawContent → lark-mcp__docx_v1_document_rawContent
- docx.v1.documentBlock.batchUpdate → lark-mcp__docx_v1_documentBlock_batchUpdate
- docx.v1.documentBlock.get → lark-mcp__docx_v1_documentBlock_get
- docx.v1.documentBlock.list → lark-mcp__docx_v1_documentBlock_list
- docx.v1.documentBlock.patch → lark-mcp__docx_v1_documentBlock_patch
- docx.v1.documentBlockChildren.batchDelete → lark-mcp__docx_v1_documentBlockChildren_batchDelete
- docx.v1.documentBlockChildren.create → lark-mcp__docx_v1_documentBlockChildren_create
- docx.v1.documentBlockChildren.get → lark-mcp__docx_v1_documentBlockChildren_get
- docx.v1.documentBlockDescendant.create → lark-mcp__docx_v1_documentBlockDescendant_create
- drive.v1.exportTask.create → lark-mcp__drive_v1_exportTask_create
- drive.v1.exportTask.get → lark-mcp__drive_v1_exportTask_get
- drive.v1.file.copy → lark-mcp__drive_v1_file_copy
- drive.v1.file.createFolder → lark-mcp__drive_v1_file_createFolder
- drive.v1.file.createShortcut → lark-mcp__drive_v1_file_createShortcut
- drive.v1.file.delete → lark-mcp__drive_v1_file_delete
- drive.v1.file.deleteSubscribe → lark-mcp__drive_v1_file_deleteSubscribe
- drive.v1.file.getSubscribe → lark-mcp__drive_v1_file_getSubscribe
- drive.v1.file.list → lark-mcp__drive_v1_file_list
- drive.v1.file.move → lark-mcp__drive_v1_file_move
- drive.v1.file.subscribe → lark-mcp__drive_v1_file_subscribe
- drive.v1.file.taskCheck → lark-mcp__drive_v1_file_taskCheck
- drive.v1.file.uploadFinish → lark-mcp__drive_v1_file_uploadFinish
- drive.v1.file.uploadPrepare → lark-mcp__drive_v1_file_uploadPrepare
- drive.v1.fileComment.batchQuery → lark-mcp__drive_v1_fileComment_batchQuery
- drive.v1.fileComment.create → lark-mcp__drive_v1_fileComment_create
- drive.v1.fileComment.get → lark-mcp__drive_v1_fileComment_get
- drive.v1.fileComment.list → lark-mcp__drive_v1_fileComment_list
- drive.v1.fileComment.patch → lark-mcp__drive_v1_fileComment_patch
- drive.v1.fileCommentReply.delete → lark-mcp__drive_v1_fileCommentReply_delete
- drive.v1.fileCommentReply.list → lark-mcp__drive_v1_fileCommentReply_list
- drive.v1.fileCommentReply.update → lark-mcp__drive_v1_fileCommentReply_update
- drive.v1.fileStatistics.get → lark-mcp__drive_v1_fileStatistics_get
- drive.v1.fileSubscription.create → lark-mcp__drive_v1_fileSubscription_create
- drive.v1.fileSubscription.get → lark-mcp__drive_v1_fileSubscription_get
- drive.v1.fileSubscription.patch → lark-mcp__drive_v1_fileSubscription_patch
- drive.v1.fileVersion.create → lark-mcp__drive_v1_fileVersion_create
- drive.v1.fileVersion.delete → lark-mcp__drive_v1_fileVersion_delete
- drive.v1.fileVersion.get → lark-mcp__drive_v1_fileVersion_get
- drive.v1.fileVersion.list → lark-mcp__drive_v1_fileVersion_list
- drive.v1.fileViewRecord.list → lark-mcp__drive_v1_fileViewRecord_list
- drive.v1.importTask.create → lark-mcp__drive_v1_importTask_create
- drive.v1.importTask.get → lark-mcp__drive_v1_importTask_get
- drive.v1.media.batchGetTmpDownloadUrl → lark-mcp__drive_v1_media_batchGetTmpDownloadUrl
- drive.v1.media.uploadFinish → lark-mcp__drive_v1_media_uploadFinish
- drive.v1.media.uploadPrepare → lark-mcp__drive_v1_media_uploadPrepare
- drive.v1.meta.batchQuery → lark-mcp__drive_v1_meta_batchQuery
- drive.v1.permissionMember.auth → lark-mcp__drive_v1_permissionMember_auth
- drive.v1.permissionMember.batchCreate → lark-mcp__drive_v1_permissionMember_batchCreate
- drive.v1.permissionMember.create → lark-mcp__drive_v1_permissionMember_create
- drive.v1.permissionMember.delete → lark-mcp__drive_v1_permissionMember_delete
- drive.v1.permissionMember.list → lark-mcp__drive_v1_permissionMember_list
- drive.v1.permissionMember.transferOwner → lark-mcp__drive_v1_permissionMember_transferOwner
- drive.v1.permissionMember.update → lark-mcp__drive_v1_permissionMember_update
- drive.v1.permissionPublic.get → lark-mcp__drive_v1_permissionPublic_get
- drive.v1.permissionPublic.patch → lark-mcp__drive_v1_permissionPublic_patch
- drive.v1.permissionPublicPassword.create → lark-mcp__drive_v1_permissionPublicPassword_create
- drive.v1.permissionPublicPassword.delete → lark-mcp__drive_v1_permissionPublicPassword_delete
- drive.v1.permissionPublicPassword.update → lark-mcp__drive_v1_permissionPublicPassword_update
- drive.v2.fileLike.list → lark-mcp__drive_v2_fileLike_list
- drive.v2.permissionPublic.get → lark-mcp__drive_v2_permissionPublic_get
- drive.v2.permissionPublic.patch → lark-mcp__drive_v2_permissionPublic_patch
- ehr.v1.employee.list → lark-mcp__ehr_v1_employee_list
- event.v1.outboundIp.list → lark-mcp__event_v1_outboundIp_list
- helpdesk.v1.notification.cancelApprove → lark-mcp__helpdesk_v1_notification_cancelApprove
- helpdesk.v1.notification.cancelSend → lark-mcp__helpdesk_v1_notification_cancelSend
- helpdesk.v1.notification.create → lark-mcp__helpdesk_v1_notification_create
- helpdesk.v1.notification.executeSend → lark-mcp__helpdesk_v1_notification_executeSend
- helpdesk.v1.notification.get → lark-mcp__helpdesk_v1_notification_get
- helpdesk.v1.notification.patch → lark-mcp__helpdesk_v1_notification_patch
- helpdesk.v1.notification.preview → lark-mcp__helpdesk_v1_notification_preview
- helpdesk.v1.notification.submitApprove → lark-mcp__helpdesk_v1_notification_submitApprove
- hire.v1.advertisement.publish → lark-mcp__hire_v1_advertisement_publish
- hire.v1.agency.batchQuery → lark-mcp__hire_v1_agency_batchQuery
- hire.v1.agency.get → lark-mcp__hire_v1_agency_get
- hire.v1.agency.getAgencyAccount → lark-mcp__hire_v1_agency_getAgencyAccount
- hire.v1.agency.operateAgencyAccount → lark-mcp__hire_v1_agency_operateAgencyAccount
- hire.v1.agency.protect → lark-mcp__hire_v1_agency_protect
- hire.v1.agency.protectSearch → lark-mcp__hire_v1_agency_protectSearch
- hire.v1.agency.query → lark-mcp__hire_v1_agency_query
- hire.v1.application.cancelOnboard → lark-mcp__hire_v1_application_cancelOnboard
- hire.v1.application.create → lark-mcp__hire_v1_application_create
- hire.v1.application.get → lark-mcp__hire_v1_application_get
- hire.v1.application.getDetail → lark-mcp__hire_v1_application_getDetail
- hire.v1.application.list → lark-mcp__hire_v1_application_list
- hire.v1.application.offer → lark-mcp__hire_v1_application_offer
- hire.v1.application.recover → lark-mcp__hire_v1_application_recover
- hire.v1.application.terminate → lark-mcp__hire_v1_application_terminate
- hire.v1.application.transferOnboard → lark-mcp__hire_v1_application_transferOnboard
- hire.v1.application.transferStage → lark-mcp__hire_v1_application_transferStage
- hire.v1.applicationInterview.list → lark-mcp__hire_v1_applicationInterview_list
- hire.v1.attachment.get → lark-mcp__hire_v1_attachment_get
- hire.v1.attachment.preview → lark-mcp__hire_v1_attachment_preview
- hire.v1.backgroundCheckOrder.list → lark-mcp__hire_v1_backgroundCheckOrder_list
- hire.v1.diversityInclusion.search → lark-mcp__hire_v1_diversityInclusion_search
- hire.v1.ecoAccountCustomField.batchDelete → lark-mcp__hire_v1_ecoAccountCustomField_batchDelete
- hire.v1.ecoAccountCustomField.batchUpdate → lark-mcp__hire_v1_ecoAccountCustomField_batchUpdate
- hire.v1.ecoAccountCustomField.create → lark-mcp__hire_v1_ecoAccountCustomField_create
- hire.v1.ecoBackgroundCheck.cancel → lark-mcp__hire_v1_ecoBackgroundCheck_cancel
- hire.v1.ecoBackgroundCheck.updateProgress → lark-mcp__hire_v1_ecoBackgroundCheck_updateProgress
- hire.v1.ecoBackgroundCheck.updateResult → lark-mcp__hire_v1_ecoBackgroundCheck_updateResult
- hire.v1.ecoBackgroundCheckCustomField.batchDelete → lark-mcp__hire_v1_ecoBackgroundCheckCustomField_batchDelete
- hire.v1.ecoBackgroundCheckCustomField.batchUpdate → lark-mcp__hire_v1_ecoBackgroundCheckCustomField_batchUpdate
- hire.v1.ecoBackgroundCheckCustomField.create → lark-mcp__hire_v1_ecoBackgroundCheckCustomField_create
- hire.v1.ecoBackgroundCheckPackage.batchDelete → lark-mcp__hire_v1_ecoBackgroundCheckPackage_batchDelete
- hire.v1.ecoBackgroundCheckPackage.batchUpdate → lark-mcp__hire_v1_ecoBackgroundCheckPackage_batchUpdate
- hire.v1.ecoBackgroundCheckPackage.create → lark-mcp__hire_v1_ecoBackgroundCheckPackage_create
- hire.v1.ecoExam.loginInfo → lark-mcp__hire_v1_ecoExam_loginInfo
- hire.v1.ecoExam.updateResult → lark-mcp__hire_v1_ecoExam_updateResult
- hire.v1.ecoExamPaper.batchDelete → lark-mcp__hire_v1_ecoExamPaper_batchDelete
- hire.v1.ecoExamPaper.batchUpdate → lark-mcp__hire_v1_ecoExamPaper_batchUpdate
- hire.v1.ecoExamPaper.create → lark-mcp__hire_v1_ecoExamPaper_create
- hire.v1.ehrImportTask.patch → lark-mcp__hire_v1_ehrImportTask_patch
- hire.v1.employee.get → lark-mcp__hire_v1_employee_get
- hire.v1.employee.getByApplication → lark-mcp__hire_v1_employee_getByApplication
- hire.v1.employee.patch → lark-mcp__hire_v1_employee_patch
- hire.v1.evaluation.list → lark-mcp__hire_v1_evaluation_list
- hire.v1.evaluationTask.list → lark-mcp__hire_v1_evaluationTask_list
- hire.v1.exam.create → lark-mcp__hire_v1_exam_create
- hire.v1.examMarkingTask.list → lark-mcp__hire_v1_examMarkingTask_list
- hire.v1.externalApplication.create → lark-mcp__hire_v1_externalApplication_create
- hire.v1.externalApplication.delete → lark-mcp__hire_v1_externalApplication_delete
- hire.v1.externalApplication.list → lark-mcp__hire_v1_externalApplication_list
- hire.v1.externalApplication.update → lark-mcp__hire_v1_externalApplication_update
- hire.v1.externalBackgroundCheck.batchQuery → lark-mcp__hire_v1_externalBackgroundCheck_batchQuery
- hire.v1.externalBackgroundCheck.create → lark-mcp__hire_v1_externalBackgroundCheck_create
- hire.v1.externalBackgroundCheck.delete → lark-mcp__hire_v1_externalBackgroundCheck_delete
- hire.v1.externalBackgroundCheck.update → lark-mcp__hire_v1_externalBackgroundCheck_update
- hire.v1.externalInterview.batchQuery → lark-mcp__hire_v1_externalInterview_batchQuery
- hire.v1.externalInterview.create → lark-mcp__hire_v1_externalInterview_create
- hire.v1.externalInterview.delete → lark-mcp__hire_v1_externalInterview_delete
- hire.v1.externalInterview.update → lark-mcp__hire_v1_externalInterview_update
- hire.v1.externalInterviewAssessment.create → lark-mcp__hire_v1_externalInterviewAssessment_create
- hire.v1.externalInterviewAssessment.patch → lark-mcp__hire_v1_externalInterviewAssessment_patch
- hire.v1.externalOffer.batchQuery → lark-mcp__hire_v1_externalOffer_batchQuery
- hire.v1.externalOffer.create → lark-mcp__hire_v1_externalOffer_create
- hire.v1.externalOffer.delete → lark-mcp__hire_v1_externalOffer_delete
- hire.v1.externalOffer.update → lark-mcp__hire_v1_externalOffer_update
- hire.v1.externalReferralReward.create → lark-mcp__hire_v1_externalReferralReward_create
- hire.v1.externalReferralReward.delete → lark-mcp__hire_v1_externalReferralReward_delete
- hire.v1.interview.getByTalent → lark-mcp__hire_v1_interview_getByTalent
- hire.v1.interview.list → lark-mcp__hire_v1_interview_list
- hire.v1.interviewer.list → lark-mcp__hire_v1_interviewer_list
- hire.v1.interviewer.patch → lark-mcp__hire_v1_interviewer_patch
- hire.v1.interviewFeedbackForm.list → lark-mcp__hire_v1_interviewFeedbackForm_list
- hire.v1.interviewRecord.get → lark-mcp__hire_v1_interviewRecord_get
- hire.v1.interviewRecord.list → lark-mcp__hire_v1_interviewRecord_list
- hire.v1.interviewRecordAttachment.get → lark-mcp__hire_v1_interviewRecordAttachment_get
- hire.v1.interviewRegistrationSchema.list → lark-mcp__hire_v1_interviewRegistrationSchema_list
- hire.v1.interviewRoundType.list → lark-mcp__hire_v1_interviewRoundType_list
- hire.v1.interviewTask.list → lark-mcp__hire_v1_interviewTask_list
- hire.v1.job.close → lark-mcp__hire_v1_job_close
- hire.v1.job.combinedCreate → lark-mcp__hire_v1_job_combinedCreate
- hire.v1.job.combinedUpdate → lark-mcp__hire_v1_job_combinedUpdate
- hire.v1.job.config → lark-mcp__hire_v1_job_config
- hire.v1.job.get → lark-mcp__hire_v1_job_get
- hire.v1.job.getDetail → lark-mcp__hire_v1_job_getDetail
- hire.v1.job.list → lark-mcp__hire_v1_job_list
- hire.v1.job.open → lark-mcp__hire_v1_job_open
- hire.v1.job.recruiter → lark-mcp__hire_v1_job_recruiter
- hire.v1.job.updateConfig → lark-mcp__hire_v1_job_updateConfig
- hire.v1.jobFunction.list → lark-mcp__hire_v1_jobFunction_list
- hire.v1.jobManager.batchUpdate → lark-mcp__hire_v1_jobManager_batchUpdate
- hire.v1.jobManager.get → lark-mcp__hire_v1_jobManager_get
- hire.v1.jobProcess.list → lark-mcp__hire_v1_jobProcess_list
- hire.v1.jobPublishRecord.search → lark-mcp__hire_v1_jobPublishRecord_search
- hire.v1.jobRequirement.create → lark-mcp__hire_v1_jobRequirement_create
- hire.v1.jobRequirement.delete → lark-mcp__hire_v1_jobRequirement_delete
- hire.v1.jobRequirement.list → lark-mcp__hire_v1_jobRequirement_list
- hire.v1.jobRequirement.listById → lark-mcp__hire_v1_jobRequirement_listById
- hire.v1.jobRequirement.update → lark-mcp__hire_v1_jobRequirement_update
- hire.v1.jobRequirementSchema.list → lark-mcp__hire_v1_jobRequirementSchema_list
- hire.v1.jobSchema.list → lark-mcp__hire_v1_jobSchema_list
- hire.v1.jobType.list → lark-mcp__hire_v1_jobType_list
- hire.v1.location.list → lark-mcp__hire_v1_location_list
- hire.v1.location.query → lark-mcp__hire_v1_location_query
- hire.v1.minutes.get → lark-mcp__hire_v1_minutes_get
- hire.v1.note.create → lark-mcp__hire_v1_note_create
- hire.v1.note.delete → lark-mcp__hire_v1_note_delete
- hire.v1.note.get → lark-mcp__hire_v1_note_get
- hire.v1.note.list → lark-mcp__hire_v1_note_list
- hire.v1.note.patch → lark-mcp__hire_v1_note_patch
- hire.v1.offer.create → lark-mcp__hire_v1_offer_create
- hire.v1.offer.get → lark-mcp__hire_v1_offer_get
- hire.v1.offer.internOfferStatus → lark-mcp__hire_v1_offer_internOfferStatus
- hire.v1.offer.list → lark-mcp__hire_v1_offer_list
- hire.v1.offer.offerStatus → lark-mcp__hire_v1_offer_offerStatus
- hire.v1.offer.update → lark-mcp__hire_v1_offer_update
- hire.v1.offerApplicationForm.get → lark-mcp__hire_v1_offerApplicationForm_get
- hire.v1.offerApplicationForm.list → lark-mcp__hire_v1_offerApplicationForm_list
- hire.v1.offerCustomField.update → lark-mcp__hire_v1_offerCustomField_update
- hire.v1.offerSchema.get → lark-mcp__hire_v1_offerSchema_get
- hire.v1.questionnaire.list → lark-mcp__hire_v1_questionnaire_list
- hire.v1.referral.getByApplication → lark-mcp__hire_v1_referral_getByApplication
- hire.v1.referral.search → lark-mcp__hire_v1_referral_search
- hire.v1.referralAccount.create → lark-mcp__hire_v1_referralAccount_create
- hire.v1.referralAccount.deactivate → lark-mcp__hire_v1_referralAccount_deactivate
- hire.v1.referralAccount.enable → lark-mcp__hire_v1_referralAccount_enable
- hire.v1.referralAccount.getAccountAssets → lark-mcp__hire_v1_referralAccount_getAccountAssets
- hire.v1.referralAccount.reconciliation → lark-mcp__hire_v1_referralAccount_reconciliation
- hire.v1.referralAccount.withdraw → lark-mcp__hire_v1_referralAccount_withdraw
- hire.v1.referralWebsiteJobPost.get → lark-mcp__hire_v1_referralWebsiteJobPost_get
- hire.v1.referralWebsiteJobPost.list → lark-mcp__hire_v1_referralWebsiteJobPost_list
- hire.v1.registrationSchema.list → lark-mcp__hire_v1_registrationSchema_list
- hire.v1.resumeSource.list → lark-mcp__hire_v1_resumeSource_list
- hire.v1.role.get → lark-mcp__hire_v1_role_get
- hire.v1.role.list → lark-mcp__hire_v1_role_list
- hire.v1.subject.list → lark-mcp__hire_v1_subject_list
- hire.v1.talent.addToFolder → lark-mcp__hire_v1_talent_addToFolder
- hire.v1.talent.batchGetId → lark-mcp__hire_v1_talent_batchGetId
- hire.v1.talent.combinedCreate → lark-mcp__hire_v1_talent_combinedCreate
- hire.v1.talent.combinedUpdate → lark-mcp__hire_v1_talent_combinedUpdate
- hire.v1.talent.get → lark-mcp__hire_v1_talent_get
- hire.v1.talent.list → lark-mcp__hire_v1_talent_list
- hire.v1.talent.onboardStatus → lark-mcp__hire_v1_talent_onboardStatus
- hire.v1.talent.removeToFolder → lark-mcp__hire_v1_talent_removeToFolder
- hire.v1.talent.tag → lark-mcp__hire_v1_talent_tag
- hire.v1.talentBlocklist.changeTalentBlock → lark-mcp__hire_v1_talentBlocklist_changeTalentBlock
- hire.v1.talentExternalInfo.create → lark-mcp__hire_v1_talentExternalInfo_create
- hire.v1.talentExternalInfo.update → lark-mcp__hire_v1_talentExternalInfo_update
- hire.v1.talentFolder.list → lark-mcp__hire_v1_talentFolder_list
- hire.v1.talentObject.query → lark-mcp__hire_v1_talentObject_query
- hire.v1.talentOperationLog.search → lark-mcp__hire_v1_talentOperationLog_search
- hire.v1.talentPool.batchChangeTalentPool → lark-mcp__hire_v1_talentPool_batchChangeTalentPool
- hire.v1.talentPool.moveTalent → lark-mcp__hire_v1_talentPool_moveTalent
- hire.v1.talentPool.search → lark-mcp__hire_v1_talentPool_search
- hire.v1.talentTag.list → lark-mcp__hire_v1_talentTag_list
- hire.v1.terminationReason.list → lark-mcp__hire_v1_terminationReason_list
- hire.v1.test.search → lark-mcp__hire_v1_test_search
- hire.v1.todo.list → lark-mcp__hire_v1_todo_list
- hire.v1.tripartiteAgreement.create → lark-mcp__hire_v1_tripartiteAgreement_create
- hire.v1.tripartiteAgreement.delete → lark-mcp__hire_v1_tripartiteAgreement_delete
- hire.v1.tripartiteAgreement.list → lark-mcp__hire_v1_tripartiteAgreement_list
- hire.v1.tripartiteAgreement.update → lark-mcp__hire_v1_tripartiteAgreement_update
- hire.v1.userRole.list → lark-mcp__hire_v1_userRole_list
- hire.v1.website.list → lark-mcp__hire_v1_website_list
- hire.v1.websiteChannel.create → lark-mcp__hire_v1_websiteChannel_create
- hire.v1.websiteChannel.delete → lark-mcp__hire_v1_websiteChannel_delete
- hire.v1.websiteChannel.list → lark-mcp__hire_v1_websiteChannel_list
- hire.v1.websiteChannel.update → lark-mcp__hire_v1_websiteChannel_update
- hire.v1.websiteDelivery.createByAttachment → lark-mcp__hire_v1_websiteDelivery_createByAttachment
- hire.v1.websiteDelivery.createByResume → lark-mcp__hire_v1_websiteDelivery_createByResume
- hire.v1.websiteDeliveryTask.get → lark-mcp__hire_v1_websiteDeliveryTask_get
- hire.v1.websiteJobPost.get → lark-mcp__hire_v1_websiteJobPost_get
- hire.v1.websiteJobPost.list → lark-mcp__hire_v1_websiteJobPost_list
- hire.v1.websiteJobPost.search → lark-mcp__hire_v1_websiteJobPost_search
- hire.v1.websiteSiteUser.create → lark-mcp__hire_v1_websiteSiteUser_create
- hire.v2.interviewRecord.get → lark-mcp__hire_v2_interviewRecord_get
- hire.v2.interviewRecord.list → lark-mcp__hire_v2_interviewRecord_list
- hire.v2.talent.get → lark-mcp__hire_v2_talent_get
- human_authentication.v1.identity.create → lark-mcp__human_authentication_v1_identity_create
- im.v1.batchMessage.delete → lark-mcp__im_v1_batchMessage_delete
- im.v1.batchMessage.getProgress → lark-mcp__im_v1_batchMessage_getProgress
- im.v1.batchMessage.readUser → lark-mcp__im_v1_batchMessage_readUser
- im.v1.chat.create → lark-mcp__im_v1_chat_create
- im.v1.chat.delete → lark-mcp__im_v1_chat_delete
- im.v1.chat.get → lark-mcp__im_v1_chat_get
- im.v1.chat.link → lark-mcp__im_v1_chat_link
- im.v1.chat.list → lark-mcp__im_v1_chat_list
- im.v1.chat.search → lark-mcp__im_v1_chat_search
- im.v1.chat.update → lark-mcp__im_v1_chat_update
- im.v1.chatAnnouncement.get → lark-mcp__im_v1_chatAnnouncement_get
- im.v1.chatAnnouncement.patch → lark-mcp__im_v1_chatAnnouncement_patch
- im.v1.chatManagers.addManagers → lark-mcp__im_v1_chatManagers_addManagers
- im.v1.chatManagers.deleteManagers → lark-mcp__im_v1_chatManagers_deleteManagers
- im.v1.chatMembers.create → lark-mcp__im_v1_chatMembers_create
- im.v1.chatMembers.delete → lark-mcp__im_v1_chatMembers_delete
- im.v1.chatMembers.get → lark-mcp__im_v1_chatMembers_get
- im.v1.chatMembers.isInChat → lark-mcp__im_v1_chatMembers_isInChat
- im.v1.chatMembers.meJoin → lark-mcp__im_v1_chatMembers_meJoin
- im.v1.chatMenuItem.patch → lark-mcp__im_v1_chatMenuItem_patch
- im.v1.chatMenuTree.create → lark-mcp__im_v1_chatMenuTree_create
- im.v1.chatMenuTree.delete → lark-mcp__im_v1_chatMenuTree_delete
- im.v1.chatMenuTree.get → lark-mcp__im_v1_chatMenuTree_get
- im.v1.chatMenuTree.sort → lark-mcp__im_v1_chatMenuTree_sort
- im.v1.chatModeration.get → lark-mcp__im_v1_chatModeration_get
- im.v1.chatModeration.update → lark-mcp__im_v1_chatModeration_update
- im.v1.chatTab.create → lark-mcp__im_v1_chatTab_create
- im.v1.chatTab.deleteTabs → lark-mcp__im_v1_chatTab_deleteTabs
- im.v1.chatTab.listTabs → lark-mcp__im_v1_chatTab_listTabs
- im.v1.chatTab.sortTabs → lark-mcp__im_v1_chatTab_sortTabs
- im.v1.chatTab.updateTabs → lark-mcp__im_v1_chatTab_updateTabs
- im.v1.chatTopNotice.deleteTopNotice → lark-mcp__im_v1_chatTopNotice_deleteTopNotice
- im.v1.chatTopNotice.putTopNotice → lark-mcp__im_v1_chatTopNotice_putTopNotice
- im.v1.message.create → lark-mcp__im_v1_message_create
- im.v1.message.delete → lark-mcp__im_v1_message_delete
- im.v1.message.forward → lark-mcp__im_v1_message_forward
- im.v1.message.get → lark-mcp__im_v1_message_get
- im.v1.message.list → lark-mcp__im_v1_message_list
- im.v1.message.mergeForward → lark-mcp__im_v1_message_mergeForward
- im.v1.message.patch → lark-mcp__im_v1_message_patch
- im.v1.message.pushFollowUp → lark-mcp__im_v1_message_pushFollowUp
- im.v1.message.readUsers → lark-mcp__im_v1_message_readUsers
- im.v1.message.reply → lark-mcp__im_v1_message_reply
- im.v1.message.update → lark-mcp__im_v1_message_update
- im.v1.message.urgentApp → lark-mcp__im_v1_message_urgentApp
- im.v1.message.urgentPhone → lark-mcp__im_v1_message_urgentPhone
- im.v1.message.urgentSms → lark-mcp__im_v1_message_urgentSms
- im.v1.messageReaction.create → lark-mcp__im_v1_messageReaction_create
- im.v1.messageReaction.delete → lark-mcp__im_v1_messageReaction_delete
- im.v1.messageReaction.list → lark-mcp__im_v1_messageReaction_list
- im.v1.pin.create → lark-mcp__im_v1_pin_create
- im.v1.pin.delete → lark-mcp__im_v1_pin_delete
- im.v1.pin.list → lark-mcp__im_v1_pin_list
- im.v1.thread.forward → lark-mcp__im_v1_thread_forward
- im.v2.appFeedCard.create → lark-mcp__im_v2_appFeedCard_create
- im.v2.appFeedCardBatch.delete → lark-mcp__im_v2_appFeedCardBatch_delete
- im.v2.appFeedCardBatch.update → lark-mcp__im_v2_appFeedCardBatch_update
- im.v2.bizEntityTagRelation.create → lark-mcp__im_v2_bizEntityTagRelation_create
- im.v2.bizEntityTagRelation.get → lark-mcp__im_v2_bizEntityTagRelation_get
- im.v2.bizEntityTagRelation.update → lark-mcp__im_v2_bizEntityTagRelation_update
- im.v2.chatButton.update → lark-mcp__im_v2_chatButton_update
- im.v2.feedCard.botTimeSentive → lark-mcp__im_v2_feedCard_botTimeSentive
- im.v2.feedCard.patch → lark-mcp__im_v2_feedCard_patch
- im.v2.tag.create → lark-mcp__im_v2_tag_create
- im.v2.tag.patch → lark-mcp__im_v2_tag_patch
- im.v2.urlPreview.batchUpdate → lark-mcp__im_v2_urlPreview_batchUpdate
- lingo.v1.classification.list → lark-mcp__lingo_v1_classification_list
- lingo.v1.draft.create → lark-mcp__lingo_v1_draft_create
- lingo.v1.draft.update → lark-mcp__lingo_v1_draft_update
- lingo.v1.entity.create → lark-mcp__lingo_v1_entity_create
- lingo.v1.entity.delete → lark-mcp__lingo_v1_entity_delete
- lingo.v1.entity.get → lark-mcp__lingo_v1_entity_get
- lingo.v1.entity.highlight → lark-mcp__lingo_v1_entity_highlight
- lingo.v1.entity.list → lark-mcp__lingo_v1_entity_list
- lingo.v1.entity.match → lark-mcp__lingo_v1_entity_match
- lingo.v1.entity.search → lark-mcp__lingo_v1_entity_search
- lingo.v1.entity.update → lark-mcp__lingo_v1_entity_update
- lingo.v1.repo.list → lark-mcp__lingo_v1_repo_list
- mail.v1.mailgroup.create → lark-mcp__mail_v1_mailgroup_create
- mail.v1.mailgroup.delete → lark-mcp__mail_v1_mailgroup_delete
- mail.v1.mailgroup.get → lark-mcp__mail_v1_mailgroup_get
- mail.v1.mailgroup.list → lark-mcp__mail_v1_mailgroup_list
- mail.v1.mailgroup.patch → lark-mcp__mail_v1_mailgroup_patch
- mail.v1.mailgroup.update → lark-mcp__mail_v1_mailgroup_update
- mail.v1.mailgroupAlias.create → lark-mcp__mail_v1_mailgroupAlias_create
- mail.v1.mailgroupAlias.delete → lark-mcp__mail_v1_mailgroupAlias_delete
- mail.v1.mailgroupAlias.list → lark-mcp__mail_v1_mailgroupAlias_list
- mail.v1.mailgroupManager.batchCreate → lark-mcp__mail_v1_mailgroupManager_batchCreate
- mail.v1.mailgroupManager.batchDelete → lark-mcp__mail_v1_mailgroupManager_batchDelete
- mail.v1.mailgroupManager.list → lark-mcp__mail_v1_mailgroupManager_list
- mail.v1.mailgroupMember.batchCreate → lark-mcp__mail_v1_mailgroupMember_batchCreate
- mail.v1.mailgroupMember.batchDelete → lark-mcp__mail_v1_mailgroupMember_batchDelete
- mail.v1.mailgroupMember.create → lark-mcp__mail_v1_mailgroupMember_create
- mail.v1.mailgroupMember.delete → lark-mcp__mail_v1_mailgroupMember_delete
- mail.v1.mailgroupMember.get → lark-mcp__mail_v1_mailgroupMember_get
- mail.v1.mailgroupMember.list → lark-mcp__mail_v1_mailgroupMember_list
- mail.v1.mailgroupPermissionMember.batchCreate → lark-mcp__mail_v1_mailgroupPermissionMember_batchCreate
- mail.v1.mailgroupPermissionMember.batchDelete → lark-mcp__mail_v1_mailgroupPermissionMember_batchDelete
- mail.v1.mailgroupPermissionMember.create → lark-mcp__mail_v1_mailgroupPermissionMember_create
- mail.v1.mailgroupPermissionMember.delete → lark-mcp__mail_v1_mailgroupPermissionMember_delete
- mail.v1.mailgroupPermissionMember.get → lark-mcp__mail_v1_mailgroupPermissionMember_get
- mail.v1.mailgroupPermissionMember.list → lark-mcp__mail_v1_mailgroupPermissionMember_list
- mail.v1.publicMailbox.create → lark-mcp__mail_v1_publicMailbox_create
- mail.v1.publicMailbox.delete → lark-mcp__mail_v1_publicMailbox_delete
- mail.v1.publicMailbox.get → lark-mcp__mail_v1_publicMailbox_get
- mail.v1.publicMailbox.list → lark-mcp__mail_v1_publicMailbox_list
- mail.v1.publicMailbox.patch → lark-mcp__mail_v1_publicMailbox_patch
- mail.v1.publicMailbox.removeToRecycleBin → lark-mcp__mail_v1_publicMailbox_removeToRecycleBin
- mail.v1.publicMailbox.update → lark-mcp__mail_v1_publicMailbox_update
- mail.v1.publicMailboxAlias.create → lark-mcp__mail_v1_publicMailboxAlias_create
- mail.v1.publicMailboxAlias.delete → lark-mcp__mail_v1_publicMailboxAlias_delete
- mail.v1.publicMailboxAlias.list → lark-mcp__mail_v1_publicMailboxAlias_list
- mail.v1.publicMailboxMember.batchCreate → lark-mcp__mail_v1_publicMailboxMember_batchCreate
- mail.v1.publicMailboxMember.batchDelete → lark-mcp__mail_v1_publicMailboxMember_batchDelete
- mail.v1.publicMailboxMember.clear → lark-mcp__mail_v1_publicMailboxMember_clear
- mail.v1.publicMailboxMember.create → lark-mcp__mail_v1_publicMailboxMember_create
- mail.v1.publicMailboxMember.delete → lark-mcp__mail_v1_publicMailboxMember_delete
- mail.v1.publicMailboxMember.get → lark-mcp__mail_v1_publicMailboxMember_get
- mail.v1.publicMailboxMember.list → lark-mcp__mail_v1_publicMailboxMember_list
- mail.v1.user.query → lark-mcp__mail_v1_user_query
- mail.v1.userMailbox.delete → lark-mcp__mail_v1_userMailbox_delete
- mail.v1.userMailboxAlias.create → lark-mcp__mail_v1_userMailboxAlias_create
- mail.v1.userMailboxAlias.delete → lark-mcp__mail_v1_userMailboxAlias_delete
- mail.v1.userMailboxAlias.list → lark-mcp__mail_v1_userMailboxAlias_list
- mail.v1.userMailboxEvent.subscribe → lark-mcp__mail_v1_userMailboxEvent_subscribe
- mail.v1.userMailboxEvent.subscription → lark-mcp__mail_v1_userMailboxEvent_subscription
- mail.v1.userMailboxEvent.unsubscribe → lark-mcp__mail_v1_userMailboxEvent_unsubscribe
- mail.v1.userMailboxFolder.create → lark-mcp__mail_v1_userMailboxFolder_create
- mail.v1.userMailboxFolder.delete → lark-mcp__mail_v1_userMailboxFolder_delete
- mail.v1.userMailboxFolder.list → lark-mcp__mail_v1_userMailboxFolder_list
- mail.v1.userMailboxFolder.patch → lark-mcp__mail_v1_userMailboxFolder_patch
- mail.v1.userMailboxMailContact.create → lark-mcp__mail_v1_userMailboxMailContact_create
- mail.v1.userMailboxMailContact.delete → lark-mcp__mail_v1_userMailboxMailContact_delete
- mail.v1.userMailboxMailContact.list → lark-mcp__mail_v1_userMailboxMailContact_list
- mail.v1.userMailboxMailContact.patch → lark-mcp__mail_v1_userMailboxMailContact_patch
- mail.v1.userMailboxMessage.get → lark-mcp__mail_v1_userMailboxMessage_get
- mail.v1.userMailboxMessage.getByCard → lark-mcp__mail_v1_userMailboxMessage_getByCard
- mail.v1.userMailboxMessage.list → lark-mcp__mail_v1_userMailboxMessage_list
- mail.v1.userMailboxMessage.send → lark-mcp__mail_v1_userMailboxMessage_send
- mail.v1.userMailboxMessageAttachment.downloadUrl → lark-mcp__mail_v1_userMailboxMessageAttachment_downloadUrl
- mail.v1.userMailboxRule.create → lark-mcp__mail_v1_userMailboxRule_create
- mail.v1.userMailboxRule.delete → lark-mcp__mail_v1_userMailboxRule_delete
- mail.v1.userMailboxRule.list → lark-mcp__mail_v1_userMailboxRule_list
- mail.v1.userMailboxRule.reorder → lark-mcp__mail_v1_userMailboxRule_reorder
- mail.v1.userMailboxRule.update → lark-mcp__mail_v1_userMailboxRule_update
- mdm.v1.userAuthDataRelation.bind → lark-mcp__mdm_v1_userAuthDataRelation_bind
- mdm.v1.userAuthDataRelation.unbind → lark-mcp__mdm_v1_userAuthDataRelation_unbind
- mdm.v3.batchCountryRegion.get → lark-mcp__mdm_v3_batchCountryRegion_get
- mdm.v3.countryRegion.list → lark-mcp__mdm_v3_countryRegion_list
- minutes.v1.minute.get → lark-mcp__minutes_v1_minute_get
- minutes.v1.minuteMedia.get → lark-mcp__minutes_v1_minuteMedia_get
- minutes.v1.minuteStatistics.get → lark-mcp__minutes_v1_minuteStatistics_get
- moments.v1.post.get → lark-mcp__moments_v1_post_get
- okr.v1.okr.batchGet → lark-mcp__okr_v1_okr_batchGet
- okr.v1.period.create → lark-mcp__okr_v1_period_create
- okr.v1.period.list → lark-mcp__okr_v1_period_list
- okr.v1.period.patch → lark-mcp__okr_v1_period_patch
- okr.v1.periodRule.list → lark-mcp__okr_v1_periodRule_list
- okr.v1.progressRecord.create → lark-mcp__okr_v1_progressRecord_create
- okr.v1.progressRecord.delete → lark-mcp__okr_v1_progressRecord_delete
- okr.v1.progressRecord.get → lark-mcp__okr_v1_progressRecord_get
- okr.v1.progressRecord.update → lark-mcp__okr_v1_progressRecord_update
- okr.v1.review.query → lark-mcp__okr_v1_review_query
- okr.v1.userOkr.list → lark-mcp__okr_v1_userOkr_list
- optical_char_recognition.v1.image.basicRecognize → lark-mcp__optical_char_recognition_v1_image_basicRecognize
- passport.v1.session.logout → lark-mcp__passport_v1_session_logout
- passport.v1.session.query → lark-mcp__passport_v1_session_query
- payroll.v1.costAllocationDetail.list → lark-mcp__payroll_v1_costAllocationDetail_list
- payroll.v1.costAllocationPlan.list → lark-mcp__payroll_v1_costAllocationPlan_list
- payroll.v1.costAllocationReport.list → lark-mcp__payroll_v1_costAllocationReport_list
- payroll.v1.datasource.list → lark-mcp__payroll_v1_datasource_list
- payroll.v1.datasourceRecord.query → lark-mcp__payroll_v1_datasourceRecord_query
- payroll.v1.datasourceRecord.save → lark-mcp__payroll_v1_datasourceRecord_save
- performance.v1.reviewData.query → lark-mcp__performance_v1_reviewData_query
- performance.v1.semester.list → lark-mcp__performance_v1_semester_list
- performance.v1.stageTask.findByPage → lark-mcp__performance_v1_stageTask_findByPage
- performance.v1.stageTask.findByUserList → lark-mcp__performance_v1_stageTask_findByUserList
- performance.v2.activity.query → lark-mcp__performance_v2_activity_query
- performance.v2.additionalInformation.import → lark-mcp__performance_v2_additionalInformation_import
- performance.v2.additionalInformation.query → lark-mcp__performance_v2_additionalInformation_query
- performance.v2.additionalInformationsBatch.delete → lark-mcp__performance_v2_additionalInformationsBatch_delete
- performance.v2.indicator.query → lark-mcp__performance_v2_indicator_query
- performance.v2.metricDetail.import → lark-mcp__performance_v2_metricDetail_import
- performance.v2.metricDetail.query → lark-mcp__performance_v2_metricDetail_query
- performance.v2.metricField.query → lark-mcp__performance_v2_metricField_query
- performance.v2.metricLib.query → lark-mcp__performance_v2_metricLib_query
- performance.v2.metricTag.list → lark-mcp__performance_v2_metricTag_list
- performance.v2.metricTemplate.query → lark-mcp__performance_v2_metricTemplate_query
- performance.v2.question.query → lark-mcp__performance_v2_question_query
- performance.v2.reviewData.query → lark-mcp__performance_v2_reviewData_query
- performance.v2.reviewee.query → lark-mcp__performance_v2_reviewee_query
- performance.v2.reviewTemplate.query → lark-mcp__performance_v2_reviewTemplate_query
- performance.v2.userGroupUserRel.write → lark-mcp__performance_v2_userGroupUserRel_write
- personal_settings.v1.systemStatus.batchClose → lark-mcp__personal_settings_v1_systemStatus_batchClose
- personal_settings.v1.systemStatus.batchOpen → lark-mcp__personal_settings_v1_systemStatus_batchOpen
- personal_settings.v1.systemStatus.create → lark-mcp__personal_settings_v1_systemStatus_create
- personal_settings.v1.systemStatus.delete → lark-mcp__personal_settings_v1_systemStatus_delete
- personal_settings.v1.systemStatus.list → lark-mcp__personal_settings_v1_systemStatus_list
- personal_settings.v1.systemStatus.patch → lark-mcp__personal_settings_v1_systemStatus_patch
- report.v1.rule.query → lark-mcp__report_v1_rule_query
- report.v1.ruleView.remove → lark-mcp__report_v1_ruleView_remove
- report.v1.task.query → lark-mcp__report_v1_task_query
- search.v2.app.create → lark-mcp__search_v2_app_create
- search.v2.dataSource.create → lark-mcp__search_v2_dataSource_create
- search.v2.dataSource.delete → lark-mcp__search_v2_dataSource_delete
- search.v2.dataSource.get → lark-mcp__search_v2_dataSource_get
- search.v2.dataSource.list → lark-mcp__search_v2_dataSource_list
- search.v2.dataSource.patch → lark-mcp__search_v2_dataSource_patch
- search.v2.dataSourceItem.create → lark-mcp__search_v2_dataSourceItem_create
- search.v2.dataSourceItem.delete → lark-mcp__search_v2_dataSourceItem_delete
- search.v2.dataSourceItem.get → lark-mcp__search_v2_dataSourceItem_get
- search.v2.message.create → lark-mcp__search_v2_message_create
- search.v2.schema.create → lark-mcp__search_v2_schema_create
- search.v2.schema.delete → lark-mcp__search_v2_schema_delete
- search.v2.schema.get → lark-mcp__search_v2_schema_get
- search.v2.schema.patch → lark-mcp__search_v2_schema_patch
- security_and_compliance.v1.openapiLog.listData → lark-mcp__security_and_compliance_v1_openapiLog_listData
- sheets.v3.spreadsheet.create → lark-mcp__sheets_v3_spreadsheet_create
- sheets.v3.spreadsheet.get → lark-mcp__sheets_v3_spreadsheet_get
- sheets.v3.spreadsheet.patch → lark-mcp__sheets_v3_spreadsheet_patch
- sheets.v3.spreadsheetSheet.find → lark-mcp__sheets_v3_spreadsheetSheet_find
- sheets.v3.spreadsheetSheet.get → lark-mcp__sheets_v3_spreadsheetSheet_get
- sheets.v3.spreadsheetSheet.moveDimension → lark-mcp__sheets_v3_spreadsheetSheet_moveDimension
- sheets.v3.spreadsheetSheet.query → lark-mcp__sheets_v3_spreadsheetSheet_query
- sheets.v3.spreadsheetSheet.replace → lark-mcp__sheets_v3_spreadsheetSheet_replace
- sheets.v3.spreadsheetSheetFilter.create → lark-mcp__sheets_v3_spreadsheetSheetFilter_create
- sheets.v3.spreadsheetSheetFilter.delete → lark-mcp__sheets_v3_spreadsheetSheetFilter_delete
- sheets.v3.spreadsheetSheetFilter.get → lark-mcp__sheets_v3_spreadsheetSheetFilter_get
- sheets.v3.spreadsheetSheetFilter.update → lark-mcp__sheets_v3_spreadsheetSheetFilter_update
- sheets.v3.spreadsheetSheetFilterView.create → lark-mcp__sheets_v3_spreadsheetSheetFilterView_create
- sheets.v3.spreadsheetSheetFilterView.delete → lark-mcp__sheets_v3_spreadsheetSheetFilterView_delete
- sheets.v3.spreadsheetSheetFilterView.get → lark-mcp__sheets_v3_spreadsheetSheetFilterView_get
- sheets.v3.spreadsheetSheetFilterView.patch → lark-mcp__sheets_v3_spreadsheetSheetFilterView_patch
- sheets.v3.spreadsheetSheetFilterView.query → lark-mcp__sheets_v3_spreadsheetSheetFilterView_query
- sheets.v3.spreadsheetSheetFilterViewCondition.create → lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_create
- sheets.v3.spreadsheetSheetFilterViewCondition.delete → lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_delete
- sheets.v3.spreadsheetSheetFilterViewCondition.get → lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_get
- sheets.v3.spreadsheetSheetFilterViewCondition.query → lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_query
- sheets.v3.spreadsheetSheetFilterViewCondition.update → lark-mcp__sheets_v3_spreadsheetSheetFilterViewCondition_update
- sheets.v3.spreadsheetSheetFloatImage.create → lark-mcp__sheets_v3_spreadsheetSheetFloatImage_create
- sheets.v3.spreadsheetSheetFloatImage.delete → lark-mcp__sheets_v3_spreadsheetSheetFloatImage_delete
- sheets.v3.spreadsheetSheetFloatImage.get → lark-mcp__sheets_v3_spreadsheetSheetFloatImage_get
- sheets.v3.spreadsheetSheetFloatImage.patch → lark-mcp__sheets_v3_spreadsheetSheetFloatImage_patch
- sheets.v3.spreadsheetSheetFloatImage.query → lark-mcp__sheets_v3_spreadsheetSheetFloatImage_query
- speech_to_text.v1.speech.fileRecognize → lark-mcp__speech_to_text_v1_speech_fileRecognize
- speech_to_text.v1.speech.streamRecognize → lark-mcp__speech_to_text_v1_speech_streamRecognize
- task.v1.task.batchDeleteCollaborator → lark-mcp__task_v1_task_batchDeleteCollaborator
- task.v1.task.batchDeleteFollower → lark-mcp__task_v1_task_batchDeleteFollower
- task.v1.task.complete → lark-mcp__task_v1_task_complete
- task.v1.task.create → lark-mcp__task_v1_task_create
- task.v1.task.delete → lark-mcp__task_v1_task_delete
- task.v1.task.get → lark-mcp__task_v1_task_get
- task.v1.task.list → lark-mcp__task_v1_task_list
- task.v1.task.patch → lark-mcp__task_v1_task_patch
- task.v1.task.uncomplete → lark-mcp__task_v1_task_uncomplete
- task.v1.taskCollaborator.create → lark-mcp__task_v1_taskCollaborator_create
- task.v1.taskCollaborator.delete → lark-mcp__task_v1_taskCollaborator_delete
- task.v1.taskCollaborator.list → lark-mcp__task_v1_taskCollaborator_list
- task.v1.taskComment.create → lark-mcp__task_v1_taskComment_create
- task.v1.taskComment.delete → lark-mcp__task_v1_taskComment_delete
- task.v1.taskComment.get → lark-mcp__task_v1_taskComment_get
- task.v1.taskComment.list → lark-mcp__task_v1_taskComment_list
- task.v1.taskComment.update → lark-mcp__task_v1_taskComment_update
- task.v1.taskFollower.create → lark-mcp__task_v1_taskFollower_create
- task.v1.taskFollower.delete → lark-mcp__task_v1_taskFollower_delete
- task.v1.taskFollower.list → lark-mcp__task_v1_taskFollower_list
- task.v1.taskReminder.create → lark-mcp__task_v1_taskReminder_create
- task.v1.taskReminder.delete → lark-mcp__task_v1_taskReminder_delete
- task.v1.taskReminder.list → lark-mcp__task_v1_taskReminder_list
- task.v2.attachment.delete → lark-mcp__task_v2_attachment_delete
- task.v2.attachment.get → lark-mcp__task_v2_attachment_get
- task.v2.attachment.list → lark-mcp__task_v2_attachment_list
- task.v2.comment.create → lark-mcp__task_v2_comment_create
- task.v2.comment.delete → lark-mcp__task_v2_comment_delete
- task.v2.comment.get → lark-mcp__task_v2_comment_get
- task.v2.comment.list → lark-mcp__task_v2_comment_list
- task.v2.comment.patch → lark-mcp__task_v2_comment_patch
- task.v2.customField.add → lark-mcp__task_v2_customField_add
- task.v2.customField.create → lark-mcp__task_v2_customField_create
- task.v2.customField.get → lark-mcp__task_v2_customField_get
- task.v2.customField.list → lark-mcp__task_v2_customField_list
- task.v2.customField.patch → lark-mcp__task_v2_customField_patch
- task.v2.customField.remove → lark-mcp__task_v2_customField_remove
- task.v2.customFieldOption.create → lark-mcp__task_v2_customFieldOption_create
- task.v2.customFieldOption.patch → lark-mcp__task_v2_customFieldOption_patch
- task.v2.section.create → lark-mcp__task_v2_section_create
- task.v2.section.delete → lark-mcp__task_v2_section_delete
- task.v2.section.get → lark-mcp__task_v2_section_get
- task.v2.section.list → lark-mcp__task_v2_section_list
- task.v2.section.patch → lark-mcp__task_v2_section_patch
- task.v2.section.tasks → lark-mcp__task_v2_section_tasks
- task.v2.task.addDependencies → lark-mcp__task_v2_task_addDependencies
- task.v2.task.addMembers → lark-mcp__task_v2_task_addMembers
- task.v2.task.addReminders → lark-mcp__task_v2_task_addReminders
- task.v2.task.addTasklist → lark-mcp__task_v2_task_addTasklist
- task.v2.task.create → lark-mcp__task_v2_task_create
- task.v2.task.delete → lark-mcp__task_v2_task_delete
- task.v2.task.get → lark-mcp__task_v2_task_get
- task.v2.task.list → lark-mcp__task_v2_task_list
- task.v2.task.patch → lark-mcp__task_v2_task_patch
- task.v2.task.removeDependencies → lark-mcp__task_v2_task_removeDependencies
- task.v2.task.removeMembers → lark-mcp__task_v2_task_removeMembers
- task.v2.task.removeReminders → lark-mcp__task_v2_task_removeReminders
- task.v2.task.removeTasklist → lark-mcp__task_v2_task_removeTasklist
- task.v2.task.tasklists → lark-mcp__task_v2_task_tasklists
- task.v2.tasklist.addMembers → lark-mcp__task_v2_tasklist_addMembers
- task.v2.tasklist.create → lark-mcp__task_v2_tasklist_create
- task.v2.tasklist.delete → lark-mcp__task_v2_tasklist_delete
- task.v2.tasklist.get → lark-mcp__task_v2_tasklist_get
- task.v2.tasklist.list → lark-mcp__task_v2_tasklist_list
- task.v2.tasklist.patch → lark-mcp__task_v2_tasklist_patch
- task.v2.tasklist.removeMembers → lark-mcp__task_v2_tasklist_removeMembers
- task.v2.tasklist.tasks → lark-mcp__task_v2_tasklist_tasks
- task.v2.tasklistActivitySubscription.create → lark-mcp__task_v2_tasklistActivitySubscription_create
- task.v2.tasklistActivitySubscription.delete → lark-mcp__task_v2_tasklistActivitySubscription_delete
- task.v2.tasklistActivitySubscription.get → lark-mcp__task_v2_tasklistActivitySubscription_get
- task.v2.tasklistActivitySubscription.list → lark-mcp__task_v2_tasklistActivitySubscription_list
- task.v2.tasklistActivitySubscription.patch → lark-mcp__task_v2_tasklistActivitySubscription_patch
- task.v2.taskSubtask.create → lark-mcp__task_v2_taskSubtask_create
- task.v2.taskSubtask.list → lark-mcp__task_v2_taskSubtask_list
- tenant.v2.tenant.query → lark-mcp__tenant_v2_tenant_query
- tenant.v2.tenantProductAssignInfo.query → lark-mcp__tenant_v2_tenantProductAssignInfo_query
- translation.v1.text.detect → lark-mcp__translation_v1_text_detect
- translation.v1.text.translate → lark-mcp__translation_v1_text_translate
- trust_party.v1.collaborationTenant.get → lark-mcp__trust_party_v1_collaborationTenant_get
- trust_party.v1.collaborationTenant.list → lark-mcp__trust_party_v1_collaborationTenant_list
- trust_party.v1.collaborationTenant.visibleOrganization → lark-mcp__trust_party_v1_collaborationTenant_visibleOrganization
- trust_party.v1.collaborationTenantCollaborationDepartment.get → lark-mcp__trust_party_v1_collaborationTenantCollaborationDepartment_get
- trust_party.v1.collaborationTenantCollaborationUser.get → lark-mcp__trust_party_v1_collaborationTenantCollaborationUser_get
- vc.v1.alert.list → lark-mcp__vc_v1_alert_list
- vc.v1.export.get → lark-mcp__vc_v1_export_get
- vc.v1.export.meetingList → lark-mcp__vc_v1_export_meetingList
- vc.v1.export.participantList → lark-mcp__vc_v1_export_participantList
- vc.v1.export.participantQualityList → lark-mcp__vc_v1_export_participantQualityList
- vc.v1.export.resourceReservationList → lark-mcp__vc_v1_export_resourceReservationList
- vc.v1.meeting.end → lark-mcp__vc_v1_meeting_end
- vc.v1.meeting.get → lark-mcp__vc_v1_meeting_get
- vc.v1.meeting.invite → lark-mcp__vc_v1_meeting_invite
- vc.v1.meeting.kickout → lark-mcp__vc_v1_meeting_kickout
- vc.v1.meeting.listByNo → lark-mcp__vc_v1_meeting_listByNo
- vc.v1.meeting.setHost → lark-mcp__vc_v1_meeting_setHost
- vc.v1.meetingList.get → lark-mcp__vc_v1_meetingList_get
- vc.v1.meetingRecording.get → lark-mcp__vc_v1_meetingRecording_get
- vc.v1.meetingRecording.setPermission → lark-mcp__vc_v1_meetingRecording_setPermission
- vc.v1.meetingRecording.start → lark-mcp__vc_v1_meetingRecording_start
- vc.v1.meetingRecording.stop → lark-mcp__vc_v1_meetingRecording_stop
- vc.v1.participantList.get → lark-mcp__vc_v1_participantList_get
- vc.v1.participantQualityList.get → lark-mcp__vc_v1_participantQualityList_get
- vc.v1.report.getDaily → lark-mcp__vc_v1_report_getDaily
- vc.v1.report.getTopUser → lark-mcp__vc_v1_report_getTopUser
- vc.v1.reserve.apply → lark-mcp__vc_v1_reserve_apply
- vc.v1.reserve.delete → lark-mcp__vc_v1_reserve_delete
- vc.v1.reserve.get → lark-mcp__vc_v1_reserve_get
- vc.v1.reserve.getActiveMeeting → lark-mcp__vc_v1_reserve_getActiveMeeting
- vc.v1.reserve.update → lark-mcp__vc_v1_reserve_update
- vc.v1.reserveConfig.patch → lark-mcp__vc_v1_reserveConfig_patch
- vc.v1.reserveConfig.reserveScope → lark-mcp__vc_v1_reserveConfig_reserveScope
- vc.v1.reserveConfigAdmin.get → lark-mcp__vc_v1_reserveConfigAdmin_get
- vc.v1.reserveConfigAdmin.patch → lark-mcp__vc_v1_reserveConfigAdmin_patch
- vc.v1.reserveConfigDisableInform.get → lark-mcp__vc_v1_reserveConfigDisableInform_get
- vc.v1.reserveConfigDisableInform.patch → lark-mcp__vc_v1_reserveConfigDisableInform_patch
- vc.v1.reserveConfigForm.get → lark-mcp__vc_v1_reserveConfigForm_get
- vc.v1.reserveConfigForm.patch → lark-mcp__vc_v1_reserveConfigForm_patch
- vc.v1.resourceReservationList.get → lark-mcp__vc_v1_resourceReservationList_get
- vc.v1.room.create → lark-mcp__vc_v1_room_create
- vc.v1.room.delete → lark-mcp__vc_v1_room_delete
- vc.v1.room.get → lark-mcp__vc_v1_room_get
- vc.v1.room.list → lark-mcp__vc_v1_room_list
- vc.v1.room.mget → lark-mcp__vc_v1_room_mget
- vc.v1.room.patch → lark-mcp__vc_v1_room_patch
- vc.v1.room.search → lark-mcp__vc_v1_room_search
- vc.v1.roomConfig.query → lark-mcp__vc_v1_roomConfig_query
- vc.v1.roomConfig.set → lark-mcp__vc_v1_roomConfig_set
- vc.v1.roomConfig.setCheckboardAccessCode → lark-mcp__vc_v1_roomConfig_setCheckboardAccessCode
- vc.v1.roomConfig.setRoomAccessCode → lark-mcp__vc_v1_roomConfig_setRoomAccessCode
- vc.v1.roomLevel.create → lark-mcp__vc_v1_roomLevel_create
- vc.v1.roomLevel.del → lark-mcp__vc_v1_roomLevel_del
- vc.v1.roomLevel.get → lark-mcp__vc_v1_roomLevel_get
- vc.v1.roomLevel.list → lark-mcp__vc_v1_roomLevel_list
- vc.v1.roomLevel.mget → lark-mcp__vc_v1_roomLevel_mget
- vc.v1.roomLevel.patch → lark-mcp__vc_v1_roomLevel_patch
- vc.v1.roomLevel.search → lark-mcp__vc_v1_roomLevel_search
- vc.v1.scopeConfig.create → lark-mcp__vc_v1_scopeConfig_create
- vc.v1.scopeConfig.get → lark-mcp__vc_v1_scopeConfig_get
- verification.v1.verification.get → lark-mcp__verification_v1_verification_get
- wiki.v1.node.search → lark-mcp__wiki_v1_node_search
- wiki.v2.space.create → lark-mcp__wiki_v2_space_create
- wiki.v2.space.get → lark-mcp__wiki_v2_space_get
- wiki.v2.space.getNode → lark-mcp__wiki_v2_space_getNode
- wiki.v2.space.list → lark-mcp__wiki_v2_space_list
- wiki.v2.spaceMember.create → lark-mcp__wiki_v2_spaceMember_create
- wiki.v2.spaceMember.delete → lark-mcp__wiki_v2_spaceMember_delete
- wiki.v2.spaceMember.list → lark-mcp__wiki_v2_spaceMember_list
- wiki.v2.spaceNode.copy → lark-mcp__wiki_v2_spaceNode_copy
- wiki.v2.spaceNode.create → lark-mcp__wiki_v2_spaceNode_create
- wiki.v2.spaceNode.list → lark-mcp__wiki_v2_spaceNode_list
- wiki.v2.spaceNode.move → lark-mcp__wiki_v2_spaceNode_move
- wiki.v2.spaceNode.moveDocsToWiki → lark-mcp__wiki_v2_spaceNode_moveDocsToWiki
- wiki.v2.spaceNode.updateTitle → lark-mcp__wiki_v2_spaceNode_updateTitle
- wiki.v2.spaceSetting.update → lark-mcp__wiki_v2_spaceSetting_update
- wiki.v2.task.get → lark-mcp__wiki_v2_task_get
- workplace.v1.customWorkplaceAccessData.search → lark-mcp__workplace_v1_customWorkplaceAccessData_search
- workplace.v1.workplaceAccessData.search → lark-mcp__workplace_v1_workplaceAccessData_search
- workplace.v1.workplaceBlockAccessData.search → lark-mcp__workplace_v1_workplaceBlockAccessData_search
