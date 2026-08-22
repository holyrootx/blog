import { getApiData } from '../../../shared/api/blogApiClient';
import { toPostCard } from '../../../shared/post/postCardMapper';

const DEFAULT_PROFILE_ID = 1;
const DEFAULT_HOME_PAGE_HERO_ID = 1;
const DEFAULT_HOME_POST_SIZE = 4;

export function getBlogProfile(profileId = DEFAULT_PROFILE_ID) {
  return getApiData(`/api/v1/blog/profile?profileId=${profileId}`);
}

export function getHomePageHero(homePageHeroId = DEFAULT_HOME_PAGE_HERO_ID) {
  return getApiData(`/api/v1/blog/home/hero?homePageHeroId=${homePageHeroId}`);
}

export async function getHomePosts(sort = 'latest', size = DEFAULT_HOME_POST_SIZE) {
  const searchParams = new URLSearchParams({
    sort,
    size: String(size),
  });

  const posts = await getApiData(`/api/v1/blog/home/posts?${searchParams.toString()}`);
  return posts.map(toPostCard);
}
