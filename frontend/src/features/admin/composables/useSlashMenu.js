import { ref } from 'vue';

import { filterSlashCommands } from '../data/postSlashCommands';

/**
 * 글자 블록에서 / 를 쳤을 때 뜨는 블록 고르기 메뉴.
 *
 * 메뉴는 한 번에 한 블록에만 뜬다. 고른 명령을 실제로 적용하는 일({@code runCommand})은
 * 블록 배열을 쥔 편집기가 한다.
 */
export function useSlashMenu({ textRefs, runCommand }) {
  const menuOpenId = ref('');
  const menuQuery = ref('');
  const menuIndex = ref(0);

  function detectSlash(block) {
    const element = textRefs.get(block.id);
    const before = element ? element.textBeforeCaret() : '';
    const match = before.match(/(?:^|\s)\/(\S*)$/);

    if (!match || filterSlashCommands(match[1]).length === 0) {
      closeMenu();
      return;
    }

    menuOpenId.value = block.id;
    menuQuery.value = match[1];
    menuIndex.value = 0;
  }

  function closeMenu() {
    menuOpenId.value = '';
    menuQuery.value = '';
    menuIndex.value = 0;
  }

  function commandsFor() {
    return filterSlashCommands(menuQuery.value);
  }

  /** 메뉴가 받은 키면 true. 그러면 편집기는 그 키를 더 다루지 않는다 */
  function handleMenuKey(block, event) {
    const commands = commandsFor();

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      menuIndex.value = (menuIndex.value + 1) % commands.length;
      return true;
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault();
      menuIndex.value = (menuIndex.value - 1 + commands.length) % commands.length;
      return true;
    }

    if (event.key === 'Enter' || event.key === 'Tab') {
      event.preventDefault();
      runCommand(block, commands[menuIndex.value]);
      return true;
    }

    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      closeMenu();
      return true;
    }

    return false;
  }

  return {
    menuOpenId,
    menuIndex,
    detectSlash,
    closeMenu,
    commandsFor,
    handleMenuKey,
  };
}
