<script setup>
import { computed, onMounted, reactive, ref } from 'vue';

import {
  getAdminBlogProfile,
  getAdminHomePageHero,
  updateAdminBlogProfile,
  updateAdminHomePageHero,
  uploadAdminImage,
} from '../api/adminApi';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import { notifySuccess } from '../data/adminToastStore';

const EMPTY_PROFILE = {
  id: 1,
  name: '',
  intro: '',
  job: '',
  avatarImageUrl: '',
  githubUrl: '',
  email: '',
  blogStartedAt: '',
};

const EMPTY_HERO = {
  id: 1,
  subTitle: '',
  title: '',
  intro: '',
  heroImageUrl: '',
};

const profile = reactive({ ...EMPTY_PROFILE });
const hero = reactive({ ...EMPTY_HERO });

const loading = ref(true);
const saving = ref(false);
const loadError = ref('');
const formError = ref('');
const avatarUploadError = ref('');
const heroUploadError = ref('');
const uploadingAvatar = ref(false);
const uploadingHero = ref(false);
const savedSnapshot = ref('');

const avatarInput = ref(null);
const heroInput = ref(null);

const currentSnapshot = computed(() => JSON.stringify({
  profile: profileRequest(),
  hero: heroRequest(),
}));

const hasChanges = computed(() => savedSnapshot.value !== ''
  && savedSnapshot.value !== currentSnapshot.value);

const busy = computed(() => loading.value
  || saving.value
  || uploadingAvatar.value
  || uploadingHero.value);

const saveLabel = computed(() => (saving.value ? '저장 중' : '변경사항 저장'));

function profileRequest() {
  return {
    id: profile.id,
    name: profile.name.trim(),
    intro: profile.intro.trim(),
    job: profile.job.trim(),
    avatarImageUrl: profile.avatarImageUrl.trim() || null,
    githubUrl: profile.githubUrl.trim() || null,
    email: profile.email.trim() || null,
    blogStartedAt: profile.blogStartedAt || null,
  };
}

function heroRequest() {
  return {
    id: hero.id,
    subTitle: hero.subTitle.trim(),
    title: hero.title.trim(),
    intro: hero.intro.trim(),
    heroImageUrl: hero.heroImageUrl.trim() || null,
  };
}

async function loadSettings() {
  loading.value = true;
  loadError.value = '';
  formError.value = '';

  try {
    const [profileResult, heroResult] = await Promise.all([
      getAdminBlogProfile(),
      getAdminHomePageHero(),
    ]);

    Object.assign(profile, EMPTY_PROFILE, profileResult);
    Object.assign(hero, EMPTY_HERO, heroResult);
    savedSnapshot.value = currentSnapshot.value;
  } catch (error) {
    console.error(error);
    loadError.value = error.message || '대문 설정을 불러오지 못했습니다.';
  } finally {
    loading.value = false;
  }
}

function validate() {
  if (!profile.name.trim() || !profile.intro.trim() || !profile.job.trim()) {
    return '프로필 이름, 소개와 직업을 입력해 주세요.';
  }

  if (!hero.subTitle.trim() || !hero.title.trim() || !hero.intro.trim()) {
    return '대문 소제목, 제목과 소개를 입력해 주세요.';
  }

  if (profile.blogStartedAt && profile.blogStartedAt > localToday()) {
    return '블로그 시작일은 오늘 이후로 설정할 수 없습니다.';
  }

  return '';
}

function localToday() {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

async function saveSettings() {
  if (busy.value || !hasChanges.value) {
    return;
  }

  formError.value = validate();

  if (formError.value) {
    return;
  }

  saving.value = true;

  try {
    await updateAdminBlogProfile(profileRequest());
    await updateAdminHomePageHero(heroRequest());

    savedSnapshot.value = currentSnapshot.value;
    notifySuccess('대문과 프로필을 저장했습니다.');
  } catch (error) {
    console.error(error);
    formError.value = error.message || '설정을 저장하지 못했습니다.';
  } finally {
    saving.value = false;
  }
}

async function uploadAvatar(event) {
  await uploadImage(event, 'avatar');
}

async function uploadHero(event) {
  await uploadImage(event, 'hero');
}

async function uploadImage(event, target) {
  const file = event.target.files?.[0];

  if (!file) {
    return;
  }

  const uploading = target === 'avatar' ? uploadingAvatar : uploadingHero;
  const errorMessage = target === 'avatar' ? avatarUploadError : heroUploadError;
  uploading.value = true;
  errorMessage.value = '';

  try {
    const image = await uploadAdminImage(file);

    if (!image.url) {
      throw new Error('업로드한 이미지 주소를 받지 못했습니다.');
    }

    if (target === 'avatar') {
      profile.avatarImageUrl = image.url;
    } else {
      hero.heroImageUrl = image.url;
    }
  } catch (error) {
    console.error(error);
    errorMessage.value = error.message || '이미지를 업로드하지 못했습니다.';
  } finally {
    uploading.value = false;
    event.target.value = '';
  }
}

function removeAvatar() {
  profile.avatarImageUrl = '';
  avatarUploadError.value = '';
}

function removeHeroImage() {
  hero.heroImageUrl = '';
  heroUploadError.value = '';
}

onMounted(loadSettings);
</script>

<template>
  <div class="admin-home-settings">
    <AdminPageHeader>
      <template #meta>
        <span v-if="hasChanges">저장하지 않은 변경사항이 있습니다.</span>
        <span v-else>공개 대문에 적용된 내용입니다.</span>
      </template>

      <template #actions>
        <button
          class="admin-button admin-button--solid"
          type="button"
          :disabled="busy || !hasChanges"
          @click="saveSettings"
        >
          {{ saveLabel }}
        </button>
      </template>
    </AdminPageHeader>

    <div v-if="loading" class="admin-settings__state" aria-live="polite">
      설정을 불러오는 중입니다.
    </div>

    <div v-else-if="loadError" class="admin-settings__state admin-settings__state--error">
      <p>{{ loadError }}</p>
      <button class="admin-button" type="button" @click="loadSettings">다시 불러오기</button>
    </div>

    <form v-else class="admin-settings" @submit.prevent="saveSettings">
      <section class="admin-settings__section">
        <header class="admin-settings__section-header">
          <div>
            <span>HOME HERO</span>
            <h2>대문</h2>
          </div>
          <span class="admin-settings__counter">{{ hero.intro.length }}자</span>
        </header>

        <div class="admin-settings__columns">
          <div class="admin-settings__fields">
            <label class="admin-field">
              <span class="admin-field__label">소제목</span>
              <input
                v-model="hero.subTitle"
                class="admin-field__input"
                type="text"
                maxlength="255"
                required
              />
            </label>

            <label class="admin-field">
              <span class="admin-field__label">제목</span>
              <input
                v-model="hero.title"
                class="admin-field__input"
                type="text"
                maxlength="255"
                required
              />
            </label>

            <label class="admin-field">
              <span class="admin-field__label">소개</span>
              <textarea
                v-model="hero.intro"
                class="admin-field__textarea"
                rows="7"
                required
              ></textarea>
            </label>
          </div>

          <div class="admin-settings__media">
            <span class="admin-field__label">대문 이미지</span>
            <div class="admin-settings__hero-preview">
              <img v-if="hero.heroImageUrl" :src="hero.heroImageUrl" alt="현재 대문" />
              <span v-else>등록된 이미지 없음</span>
            </div>

            <div class="admin-settings__media-actions">
              <button class="admin-button" type="button" :disabled="busy" @click="heroInput?.click()">
                {{ uploadingHero ? '업로드 중' : '이미지 선택' }}
              </button>
              <button
                v-if="hero.heroImageUrl"
                class="admin-button"
                type="button"
                :disabled="busy"
                @click="removeHeroImage"
              >
                이미지 제거
              </button>
            </div>
            <input
              ref="heroInput"
              class="admin-settings__file-input"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              @change="uploadHero"
            />
            <p v-if="heroUploadError" class="admin-settings__field-error">{{ heroUploadError }}</p>
          </div>
        </div>
      </section>

      <section class="admin-settings__section">
        <header class="admin-settings__section-header">
          <div>
            <span>BLOG PROFILE</span>
            <h2>프로필</h2>
          </div>
          <span v-if="profile.blogStartedAt" class="admin-settings__counter">{{ profile.blogStartedAt }} 시작</span>
        </header>

        <div class="admin-settings__columns">
          <div class="admin-settings__fields admin-settings__fields--profile">
            <label class="admin-field">
              <span class="admin-field__label">이름</span>
              <input
                v-model="profile.name"
                class="admin-field__input"
                type="text"
                maxlength="100"
                required
              />
            </label>

            <label class="admin-field">
              <span class="admin-field__label">직업</span>
              <input
                v-model="profile.job"
                class="admin-field__input"
                type="text"
                maxlength="100"
                required
              />
            </label>

            <label class="admin-field admin-settings__wide-field">
              <span class="admin-field__label">소개</span>
              <textarea
                v-model="profile.intro"
                class="admin-field__textarea"
                rows="5"
                maxlength="500"
                required
              ></textarea>
            </label>

            <label class="admin-field">
              <span class="admin-field__label">블로그 시작일</span>
              <input
                v-model="profile.blogStartedAt"
                class="admin-field__input"
                type="date"
                :max="localToday()"
              />
            </label>

            <label class="admin-field">
              <span class="admin-field__label">이메일</span>
              <input
                v-model="profile.email"
                class="admin-field__input"
                type="email"
                maxlength="255"
              />
            </label>

            <label class="admin-field admin-settings__wide-field">
              <span class="admin-field__label">GitHub 주소</span>
              <input
                v-model="profile.githubUrl"
                class="admin-field__input"
                type="url"
                maxlength="500"
              />
            </label>
          </div>

          <div class="admin-settings__media admin-settings__media--avatar">
            <span class="admin-field__label">프로필 이미지</span>
            <div class="admin-settings__avatar-preview">
              <img v-if="profile.avatarImageUrl" :src="profile.avatarImageUrl" :alt="`${profile.name} 프로필`" />
              <span v-else>{{ profile.name.slice(0, 1) || 'P' }}</span>
            </div>

            <div class="admin-settings__media-actions">
              <button class="admin-button" type="button" :disabled="busy" @click="avatarInput?.click()">
                {{ uploadingAvatar ? '업로드 중' : '이미지 선택' }}
              </button>
              <button
                v-if="profile.avatarImageUrl"
                class="admin-button"
                type="button"
                :disabled="busy"
                @click="removeAvatar"
              >
                이미지 제거
              </button>
            </div>
            <input
              ref="avatarInput"
              class="admin-settings__file-input"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              @change="uploadAvatar"
            />
            <p v-if="avatarUploadError" class="admin-settings__field-error">{{ avatarUploadError }}</p>
          </div>
        </div>
      </section>

      <p v-if="formError" class="admin-form-error" role="alert">{{ formError }}</p>

      <div class="admin-settings__footer">
        <button
          class="admin-button admin-button--solid"
          type="submit"
          :disabled="busy || !hasChanges"
        >
          {{ saveLabel }}
        </button>
      </div>
    </form>
  </div>
</template>
