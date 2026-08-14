package com.dai.flipper.domain.service

import com.dai.flipper.domain.model.CommandAction
import com.dai.flipper.domain.model.Permission
import com.dai.flipper.domain.model.PermissionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages path-based permissions for file operations.
 * Permissions are scoped, time-limited, and explicitly granted.
 */
@Singleton
class PermissionService @Inject constructor() {

    private val permissions = ConcurrentHashMap<String, Permission>()
    private val unlockedProtectedPaths = ConcurrentHashMap<String, Long>()
    private val pendingRequests = ConcurrentHashMap<String, PermissionRequest>()

    private val _activePermissions = MutableStateFlow<List<Permission>>(emptyList())
    val activePermissions: StateFlow<List<Permission>> = _activePermissions.asStateFlow()

    private val _pendingRequest = MutableStateFlow<PermissionRequest?>(null)
    val pendingRequest: StateFlow<PermissionRequest?> = _pendingRequest.asStateFlow()

    /**
     * Check if an action on a path is permitted
     */
    fun hasPermission(path: String, action: CommandAction): Boolean {
        cleanupExpired()
        return permissions.values.any { it.matches(path, action) }
    }

    /**
     * Grant a permission
     */
    fun grantPermission(permission: Permission) {
        permissions[permission.id] = permission
        updateActivePermissions()
    }

    /**
     * Grant permission for a specific path and actions
     */
    fun grantPathPermission(
        path: String,
        actions: Set<CommandAction>,
        duration: Long = Permission.DURATION_15_MINUTES
    ): Permission {
        val permission = Permission.createForPath(path, actions, duration)
        grantPermission(permission)
        return permission
    }

    /**
     * Grant project scope permission
     */
    fun grantProjectScope(projectPath: String): Permission {
        val permission = Permission.createProjectScope(projectPath)
        grantPermission(permission)
        return permission
    }

    /**
     * Revoke a permission by ID
     */
    fun revokePermission(permissionId: String): Boolean {
        val removed = permissions.remove(permissionId) != null
        if (removed) updateActivePermissions()
        return removed
    }

    /**
     * Revoke all permissions for a path pattern
     */
    fun revokePathPermissions(pathPattern: String) {
        permissions.entries.removeIf { it.value.pathPattern == pathPattern }
        updateActivePermissions()
    }

    /**
     * Revoke all permissions
     */
    fun revokeAll() {
        permissions.clear()
        unlockedProtectedPaths.clear()
        updateActivePermissions()
    }

    /**
     * Check if a protected path has been unlocked in settings
     */
    fun isProtectedPathUnlocked(path: String): Boolean {
        val expiry = unlockedProtectedPaths[path] ?: return false
        if (System.currentTimeMillis() > expiry) {
            unlockedProtectedPaths.remove(path)
            return false
        }
        return true
    }

    /**
     * Unlock a protected path (from settings)
     */
    fun unlockProtectedPath(path: String, duration: Long = Permission.DURATION_1_HOUR) {
        unlockedProtectedPaths[path] = System.currentTimeMillis() + duration
    }

    /**
     * Lock a protected path
     */
    fun lockProtectedPath(path: String) {
        unlockedProtectedPaths.remove(path)
    }

    /**
     * Get all active (non-expired) permissions
     */
    fun getActivePermissions(): List<Permission> {
        cleanupExpired()
        return permissions.values.filter { it.isValid() }.toList()
    }

    /**
     * Request permission (queues for user approval)
     */
    fun requestPermission(request: PermissionRequest) {
        pendingRequests[request.id] = request
        _pendingRequest.value = request
    }

    /**
     * Approve a pending permission request
     */
    fun approveRequest(requestId: String, duration: Long = Permission.DURATION_15_MINUTES): Permission? {
        val request = pendingRequests.remove(requestId) ?: return null
        if (_pendingRequest.value?.id == requestId) {
            _pendingRequest.value = null
        }

        val permission = Permission.createForPath(
            request.path,
            setOf(request.action),
            duration
        )
        grantPermission(permission)
        return permission
    }

    /**
     * Deny a pending permission request
     */
    fun denyRequest(requestId: String) {
        pendingRequests.remove(requestId)
        if (_pendingRequest.value?.id == requestId) {
            _pendingRequest.value = null
        }
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        permissions.entries.removeIf { !it.value.isValid() }
        unlockedProtectedPaths.entries.removeIf { it.value < now }
    }

    private fun updateActivePermissions() {
        _activePermissions.value = getActivePermissions()
    }
}
