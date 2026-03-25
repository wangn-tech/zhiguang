package handler

import (
	"net/http"
	"zhiguang/internal/handler/dto"
	"zhiguang/internal/service"

	"github.com/gin-gonic/gin"
)

// ProfileHandler 处理资料查询、更新与头像上传。
type ProfileHandler struct {
	profileService service.ProfileService
	storageService service.ObjectStorageService
}

// NewProfileHandler 创建资料处理器。
func NewProfileHandler(profileService service.ProfileService, storageService service.ObjectStorageService) *ProfileHandler {
	return &ProfileHandler{
		profileService: profileService,
		storageService: storageService,
	}
}

// Get 查询当前用户资料。
func (h *ProfileHandler) Get(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	resp, err := h.profileService.GetProfile(c.Request.Context(), userID)
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, mapProfileResponse(resp))
}

// Patch 更新当前用户资料。
func (h *ProfileHandler) Patch(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	var req dto.ProfilePatchRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.Error(err)
		return
	}

	resp, err := h.profileService.UpdateProfile(c.Request.Context(), userID, service.ProfileUpdateRequest{
		Nickname: req.Nickname,
		Bio:      req.Bio,
		ZgID:     req.ZgID,
		Gender:   req.Gender,
		Birthday: req.Birthday,
		School:   req.School,
		Email:    req.Email,
		Phone:    req.Phone,
		TagJSON:  req.TagJSON,
	})
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, mapProfileResponse(resp))
}

// UploadAvatar 上传头像并更新用户资料。
func (h *ProfileHandler) UploadAvatar(c *gin.Context) {
	userID, err := AuthUserIDFromContext(c)
	if err != nil {
		c.Error(err)
		return
	}

	file, err := c.FormFile("file")
	if err != nil {
		c.Error(err)
		return
	}

	avatarURL, err := h.storageService.UploadAvatar(c.Request.Context(), userID, file)
	if err != nil {
		c.Error(err)
		return
	}

	resp, err := h.profileService.UpdateAvatar(c.Request.Context(), userID, avatarURL)
	if err != nil {
		c.Error(err)
		return
	}

	c.JSON(http.StatusOK, mapProfileResponse(resp))
}

func mapProfileResponse(resp service.ProfileResponse) dto.ProfileResponse {
	return dto.ProfileResponse{
		ID:       int64(resp.ID),
		Nickname: resp.Nickname,
		Avatar:   resp.Avatar,
		Bio:      resp.Bio,
		ZgID:     resp.ZgID,
		Gender:   resp.Gender,
		Birthday: resp.Birthday,
		School:   resp.School,
		Phone:    resp.Phone,
		Email:    resp.Email,
		TagJSON:  resp.TagJSON,
	}
}
