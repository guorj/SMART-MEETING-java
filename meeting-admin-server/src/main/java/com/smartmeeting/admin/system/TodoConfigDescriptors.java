package com.smartmeeting.admin.system;

import org.springframework.stereotype.Component;

@Component
class TodoExtractionEnabledDescriptor extends AbstractBooleanDescriptor {
    TodoExtractionEnabledDescriptor() {
        super("meeting.todo.extraction-enabled", "minute", "false", "纪要完成后是否提取待办",
                "meeting.minute.generation-enabled");
    }
}
