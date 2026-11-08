package com.agentdesktop.server.agent.settings;

import com.agentdesktop.server.agent.extension.HookDefinition;
import com.agentdesktop.server.dto.HooksSettingsDto;
import com.agentdesktop.server.persistence.GlobalHooksStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HooksSettingsService {

    private final GlobalHooksStore globalHooksStore;

    public HooksSettingsService(GlobalHooksStore globalHooksStore) {
        this.globalHooksStore = globalHooksStore;
    }

    public HooksSettingsDto getHooks() {
        HooksSettingsDto dto = new HooksSettingsDto();
        dto.setHooks(globalHooksStore.loadHooks());
        dto.setStorage(GlobalHooksStore.MYSQL_LABEL + " / Redis cache");
        return dto;
    }

    public HooksSettingsDto updateHooks(HooksSettingsDto body) {
        List<HookDefinition> hooks = body == null || body.getHooks() == null ? List.of() : body.getHooks();
        globalHooksStore.saveHooks(hooks);
        return getHooks();
    }
}
