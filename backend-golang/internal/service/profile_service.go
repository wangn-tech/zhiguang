package service

import (
	"context"
	"regexp"
	"strings"
	"time"
	"zhiguang/internal/model"
	"zhiguang/internal/repository"
	"zhiguang/pkg/errorsx"

	"gorm.io/gorm"
)

var profileZgIDPattern = regexp.MustCompile(`^[a-zA-Z0-9_]{4,32}$`)

// ProfileService 负责用户资料更新与头像更新后的资料回读。
type ProfileService interface {
	GetProfile(ctx context.Context, userID uint64) (ProfileResponse, error)
	UpdateProfile(ctx context.Context, userID uint64, req ProfileUpdateRequest) (ProfileResponse, error)
	UpdateAvatar(ctx context.Context, userID uint64, avatarURL string) (ProfileResponse, error)
}

type profileService struct {
	users *repository.UserRepository
}

// ProfileUpdateRequest 表示资料更新输入。
type ProfileUpdateRequest struct {
	Nickname *string
	Bio      *string
	ZgID     *string
	Gender   *string
	Birthday *string
	School   *string
	Email    *string
	Phone    *string
	TagJSON  *string
}

// ProfileResponse 表示资料更新输出。
type ProfileResponse struct {
	ID       uint64
	Nickname string
	Avatar   string
	Bio      *string
	ZgID     *string
	Gender   *string
	Birthday *string
	School   *string
	Phone    *string
	Email    *string
	TagJSON  *string
}

// NewProfileService 创建资料服务。
func NewProfileService(users *repository.UserRepository) ProfileService {
	return &profileService{users: users}
}

// GetProfile 读取当前用户资料快照。
func (s *profileService) GetProfile(ctx context.Context, userID uint64) (ProfileResponse, error) {
	user, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return ProfileResponse{}, err
	}
	if user == nil {
		return ProfileResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "用户不存在")
	}
	return mapProfileResponse(user), nil
}

// UpdateProfile 校验并更新资料字段，随后返回最新用户快照。
func (s *profileService) UpdateProfile(ctx context.Context, userID uint64, req ProfileUpdateRequest) (ProfileResponse, error) {
	current, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return ProfileResponse{}, err
	}
	if current == nil {
		return ProfileResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "用户不存在")
	}

	updates := make(map[string]any)

	if req.Nickname != nil {
		nickname := strings.TrimSpace(*req.Nickname)
		if nickname == "" {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "昵称不能为空")
		}
		if len([]rune(nickname)) > 64 {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "昵称长度需在 1-64 之间")
		}
		updates["nickname"] = nickname
	}

	if req.Bio != nil {
		bio := strings.TrimSpace(*req.Bio)
		if len([]rune(bio)) > 512 {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "个人描述长度不能超过 512")
		}
		updates["bio"] = bio
	}

	if req.ZgID != nil {
		zgID := strings.TrimSpace(*req.ZgID)
		if !profileZgIDPattern.MatchString(zgID) {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "知光号仅支持字母、数字、下划线，长度 4-32")
		}
		exists, err := s.users.ExistsByZgIDExceptID(ctx, zgID, userID)
		if err != nil {
			return ProfileResponse{}, err
		}
		if exists {
			return ProfileResponse{}, errorsx.New(errorsx.CodeZgIDExists, "知光号已存在")
		}
		updates["zg_id"] = zgID
	}

	if req.Gender != nil {
		gender := strings.ToUpper(strings.TrimSpace(*req.Gender))
		if gender != "MALE" && gender != "FEMALE" && gender != "OTHER" && gender != "UNKNOWN" {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "性别取值为 MALE/FEMALE/OTHER/UNKNOWN")
		}
		updates["gender"] = gender
	}

	if req.Birthday != nil {
		birthdayRaw := strings.TrimSpace(*req.Birthday)
		birthday, err := time.Parse("2006-01-02", birthdayRaw)
		if err != nil {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "生日格式应为 yyyy-MM-dd")
		}
		if birthday.After(time.Now()) {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "生日不能晚于今天")
		}
		updates["birthday"] = birthday
	}

	if req.School != nil {
		school := strings.TrimSpace(*req.School)
		if len([]rune(school)) > 128 {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "学校名称长度不能超过 128")
		}
		updates["school"] = school
	}

	if req.Email != nil {
		email := strings.ToLower(strings.TrimSpace(*req.Email))
		if email == "" || !emailPattern.MatchString(email) {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "邮箱格式错误")
		}
		exists, err := s.users.ExistsByEmailExceptID(ctx, email, userID)
		if err != nil {
			return ProfileResponse{}, err
		}
		if exists {
			return ProfileResponse{}, errorsx.New(errorsx.CodeIdentifierExists, "邮箱已被占用")
		}
		updates["email"] = email
	}

	if req.Phone != nil {
		phone := strings.TrimSpace(*req.Phone)
		if phone == "" || !phonePattern.MatchString(phone) {
			return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "手机号格式错误")
		}
		exists, err := s.users.ExistsByPhoneExceptID(ctx, phone, userID)
		if err != nil {
			return ProfileResponse{}, err
		}
		if exists {
			return ProfileResponse{}, errorsx.New(errorsx.CodeIdentifierExists, "手机号已被占用")
		}
		updates["phone"] = phone
	}

	if req.TagJSON != nil {
		tagJSON := strings.TrimSpace(*req.TagJSON)
		updates["tags_json"] = tagJSON
	}

	if len(updates) == 0 {
		return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "未提交任何更新字段")
	}

	if err := s.users.UpdateProfileFields(ctx, userID, updates); err != nil {
		if err == gorm.ErrRecordNotFound {
			return ProfileResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "用户不存在")
		}
		if isProfileDuplicateKey(err) {
			return ProfileResponse{}, errorsx.New(errorsx.CodeIdentifierExists, "资料字段重复")
		}
		return ProfileResponse{}, err
	}

	updated, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return ProfileResponse{}, err
	}
	if updated == nil {
		return ProfileResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "用户不存在")
	}
	return mapProfileResponse(updated), nil
}

// UpdateAvatar 保存头像地址并返回最新资料。
func (s *profileService) UpdateAvatar(ctx context.Context, userID uint64, avatarURL string) (ProfileResponse, error) {
	if strings.TrimSpace(avatarURL) == "" {
		return ProfileResponse{}, errorsx.New(errorsx.CodeBadRequest, "头像地址不能为空")
	}

	if err := s.users.UpdateProfileFields(ctx, userID, map[string]any{"avatar": avatarURL}); err != nil {
		if err == gorm.ErrRecordNotFound {
			return ProfileResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "用户不存在")
		}
		return ProfileResponse{}, err
	}

	updated, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return ProfileResponse{}, err
	}
	if updated == nil {
		return ProfileResponse{}, errorsx.New(errorsx.CodeIdentifierNotFound, "用户不存在")
	}
	return mapProfileResponse(updated), nil
}

func mapProfileResponse(user *model.User) ProfileResponse {
	resp := ProfileResponse{
		ID:       user.ID,
		Nickname: user.Nickname,
		Bio:      user.Bio,
		ZgID:     user.ZgID,
		Gender:   user.Gender,
		School:   user.School,
		Phone:    user.Phone,
		Email:    user.Email,
		TagJSON:  user.TagsJSON,
	}
	if user.Avatar != nil {
		resp.Avatar = *user.Avatar
	}
	if user.Birthday != nil {
		birthday := user.Birthday.Format("2006-01-02")
		resp.Birthday = &birthday
	}
	return resp
}

func isProfileDuplicateKey(err error) bool {
	if err == nil {
		return false
	}
	lower := strings.ToLower(err.Error())
	return strings.Contains(lower, "duplicate") && strings.Contains(lower, "entry")
}
