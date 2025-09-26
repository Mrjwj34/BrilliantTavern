package com.github.jwj.brilliantavern.controller;

import com.github.jwj.brilliantavern.dto.ApiResponse;
import com.github.jwj.brilliantavern.dto.CharacterCardMarketFilter;
import com.github.jwj.brilliantavern.dto.CharacterCardResponse;
import com.github.jwj.brilliantavern.dto.CreateCharacterCardRequest;
import com.github.jwj.brilliantavern.dto.LikeResponse;
import com.github.jwj.brilliantavern.dto.CursorPageResponse;
import com.github.jwj.brilliantavern.dto.UpdateCharacterCardRequest;
import com.github.jwj.brilliantavern.security.UserPrincipal;
import com.github.jwj.brilliantavern.service.CharacterCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 角色卡控制器
 */
@Tag(name = "角色卡管理", description = "角色卡的创建、修改、删除、查询和点赞功能")
@Slf4j
@RestController
@RequestMapping("/character-cards")
@RequiredArgsConstructor
public class CharacterCardController {

    private final CharacterCardService characterCardService;

    /**
     * 创建角色卡
     */
    @Operation(summary = "创建角色卡", description = "用户创建一个新的角色卡")
    @PostMapping
    public ResponseEntity<ApiResponse<CharacterCardResponse>> createCharacterCard(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateCharacterCardRequest request) {
        
        log.info("创建角色卡请求: 用户={}, 角色名称={}", userPrincipal.getId(), request.getName());
        
        CharacterCardResponse response = characterCardService.createCharacterCard(userPrincipal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("角色卡创建成功", response));
    }

    /**
     * 角色市场综合查询（游标分页）
     */
    @Operation(summary = "获取角色市场列表", description = "支持搜索、筛选和游标分页的角色市场数据")
    @GetMapping("/market")
    public ResponseEntity<ApiResponse<CursorPageResponse<CharacterCardResponse>>> getMarketCharacterCards(
            @RequestParam(defaultValue = "public") String filter,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        UUID currentUserId = userPrincipal != null ? userPrincipal.getId() : null;
        CharacterCardMarketFilter marketFilter = CharacterCardMarketFilter.fromString(filter);
        CursorPageResponse<CharacterCardResponse> response = characterCardService.getMarketCards(
                marketFilter,
                keyword,
                cursor,
                size,
                currentUserId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 更新角色卡
     */
    @Operation(summary = "更新角色卡", description = "用户更新自己创建的角色卡信息")
    @PutMapping("/{cardId}")
    public ResponseEntity<ApiResponse<CharacterCardResponse>> updateCharacterCard(
            @Parameter(description = "角色卡ID") @PathVariable UUID cardId,
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateCharacterCardRequest request) {
        
        log.info("更新角色卡请求: ID={}, 用户={}", cardId, userPrincipal.getId());
        
        CharacterCardResponse response = characterCardService.updateCharacterCard(cardId, userPrincipal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("角色卡更新成功", response));
    }

    /**
     * 删除角色卡
     */
    @Operation(summary = "删除角色卡", description = "用户删除自己创建的角色卡")
    @DeleteMapping("/{cardId}")
    public ResponseEntity<ApiResponse<Void>> deleteCharacterCard(
            @Parameter(description = "角色卡ID") @PathVariable UUID cardId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        log.info("删除角色卡请求: ID={}, 用户={}", cardId, userPrincipal.getId());
        
        characterCardService.deleteCharacterCard(cardId, userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success("角色卡删除成功", null));
    }

    /**
     * 获取角色卡详情
     */
    @Operation(summary = "获取角色卡详情", description = "根据ID获取角色卡的详细信息")
    @GetMapping("/{cardId}")
    public ResponseEntity<ApiResponse<CharacterCardResponse>> getCharacterCard(
            @Parameter(description = "角色卡ID") @PathVariable UUID cardId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        UUID currentUserId = userPrincipal != null ? userPrincipal.getId() : null;
        CharacterCardResponse response = characterCardService.getCharacterCard(cardId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 获取公开角色卡列表
     */
    @Operation(summary = "获取公开角色卡列表", description = "分页获取所有公开的角色卡，按点赞数和创建时间排序")
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Page<CharacterCardResponse>>> getPublicCharacterCards(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        UUID currentUserId = userPrincipal != null ? userPrincipal.getId() : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<CharacterCardResponse> response = characterCardService.getPublicCharacterCards(pageable, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 搜索公开角色卡
     */
    @Operation(summary = "搜索公开角色卡", description = "根据关键词搜索公开的角色卡")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<CharacterCardResponse>>> searchCharacterCards(
            @Parameter(description = "搜索关键词") @RequestParam String keyword,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        UUID currentUserId = userPrincipal != null ? userPrincipal.getId() : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<CharacterCardResponse> response = characterCardService.searchPublicCharacterCards(keyword, pageable, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 获取热门角色卡
     */
    @Operation(summary = "获取热门角色卡", description = "获取按点赞数排序的热门角色卡")
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<Page<CharacterCardResponse>>> getPopularCharacterCards(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        UUID currentUserId = userPrincipal != null ? userPrincipal.getId() : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<CharacterCardResponse> response = characterCardService.getPopularCharacterCards(pageable, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 获取最新角色卡
     */
    @Operation(summary = "获取最新角色卡", description = "获取按创建时间排序的最新角色卡")
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<Page<CharacterCardResponse>>> getLatestCharacterCards(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        UUID currentUserId = userPrincipal != null ? userPrincipal.getId() : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<CharacterCardResponse> response = characterCardService.getLatestCharacterCards(pageable, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 获取用户创建的角色卡列表
     */
    @Operation(summary = "获取我的角色卡", description = "获取当前用户创建的所有角色卡")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<CharacterCardResponse>>> getMyCharacterCards(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<CharacterCardResponse> response = characterCardService.getUserCharacterCards(
                userPrincipal.getId(), pageable, userPrincipal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 获取指定用户创建的角色卡列表
     */
    @Operation(summary = "获取指定用户的角色卡", description = "获取指定用户创建的所有公开角色卡")
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<CharacterCardResponse>>> getUserCharacterCards(
            @Parameter(description = "用户ID") @PathVariable UUID userId,
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        UUID currentUserId = userPrincipal != null ? userPrincipal.getId() : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<CharacterCardResponse> response = characterCardService.getUserCharacterCards(userId, pageable, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 点赞/取消点赞角色卡
     */
    @Operation(summary = "点赞/取消点赞角色卡", description = "切换角色卡的点赞状态")
    @PostMapping("/{cardId}/like")
    public ResponseEntity<ApiResponse<LikeResponse>> toggleLike(
            @Parameter(description = "角色卡ID") @PathVariable UUID cardId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        log.info("切换点赞状态: 角色卡={}, 用户={}", cardId, userPrincipal.getId());
        
        LikeResponse likeResponse = characterCardService.toggleLike(cardId, userPrincipal.getId());
        String message = likeResponse.isLiked() ? "点赞成功" : "取消点赞成功";
        return ResponseEntity.ok(ApiResponse.success(message, likeResponse));
    }

    /**
     * 获取用户点赞的角色卡列表
     */
    @Operation(summary = "获取我的点赞列表", description = "获取当前用户点赞的所有角色卡")
    @GetMapping("/liked")
    public ResponseEntity<ApiResponse<Page<CharacterCardResponse>>> getLikedCharacterCards(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<CharacterCardResponse> response = characterCardService.getUserLikedCards(userPrincipal.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
