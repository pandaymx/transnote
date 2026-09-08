package com.transnote.document.event;

import com.transnote.board.event.CardCheckedEvent;
import com.transnote.document.service.BlockService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 监听卡片勾选事件（V9）：回写源文档 todo 块 checked，完成看板→文档双向同步闭环。 */
@Component
public class CardCheckedListener {

  private final BlockService blockService;

  public CardCheckedListener(BlockService blockService) {
    this.blockService = blockService;
  }

  @EventListener
  public void onCardChecked(CardCheckedEvent event) {
    if (event.sourceDocumentId() == null || event.sourceBlockId() == null) {
      return;
    }
    blockService.setTodoChecked(event.sourceBlockId(), event.checked());
  }
}
