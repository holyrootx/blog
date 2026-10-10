<script>
import { h } from 'vue';

// 인라인 서식 토큰을 그린다.
// HTML 문자열을 v-html 로 꽂지 않고 토큰마다 태그를 만들어 Vue 가 이스케이프하게 둔다.
// 색은 정해 둔 이름만 클래스로 붙인다(매퍼가 이름을 거른다) — 본문에서 임의의 CSS 를 받지 않는다.
const TAGS = { bold: 'strong', italic: 'em', strike: 's', underline: 'u' };

function renderToken(token) {
  let node = token.text;

  if (TAGS[token.type]) {
    node = h(TAGS[token.type], token.text);
  } else if (token.type === 'code') {
    node = h('code', { class: 'post-body__inline-code' }, token.text);
  } else if (token.type === 'link') {
    // 본문 링크는 외부로 나가는 경우가 많아 새 탭으로 열고 referrer 를 넘기지 않는다
    node = h('a', {
      class: 'post-body__link',
      href: token.href,
      target: '_blank',
      rel: 'noopener noreferrer',
    }, token.text);
  }

  return token.color ? h('span', { class: ['post-color', `post-color--${token.color}`] }, [node]) : node;
}

export default {
  name: 'PostInline',
  props: {
    tokens: {
      type: Array,
      default: () => [],
    },
  },
  setup(props) {
    return () => props.tokens.map(renderToken);
  },
};
</script>
