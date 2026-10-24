package app.nebula.vpn.data.repository

import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.data.database.dao.RuleDao
import app.nebula.vpn.data.database.entity.RuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingRepository @Inject constructor(
    private val ruleDao: RuleDao
) {
    val allRules: Flow<List<RoutingRule>> = ruleDao.getAllRules().map { entities ->
        entities.map { it.toModel() }
    }

    suspend fun saveRule(rule: RoutingRule) {
        ruleDao.insertRule(rule.toEntity())
    }

    suspend fun saveRules(rules: List<RoutingRule>) {
        ruleDao.insertRules(rules.map { it.toEntity() })
    }

    suspend fun deleteRule(id: String) {
        ruleDao.deleteRuleById(id)
    }

    suspend fun updateRulesOrder(orderedRules: List<RoutingRule>) {
        val updated = orderedRules.mapIndexed { index, rule ->
            rule.copy(priority = index).toEntity()
        }
        ruleDao.insertRules(updated)
    }

    private fun RuleEntity.toModel(): RoutingRule = RoutingRule(
        id = id,
        type = RuleType.valueOf(type),
        value = value,
        action = RuleAction.valueOf(action),
        targetOutbound = targetOutbound,
        priority = priority,
        isEnabled = isEnabled
    )

    private fun RoutingRule.toEntity(): RuleEntity = RuleEntity(
        id = id,
        type = type.name,
        value = value,
        action = action.name,
        targetOutbound = targetOutbound,
        priority = priority,
        isEnabled = isEnabled
    )
}
