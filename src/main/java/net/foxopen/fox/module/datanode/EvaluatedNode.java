package net.foxopen.fox.module.datanode;

import net.foxopen.fox.ContextUElem;
import net.foxopen.fox.XFUtil;
import net.foxopen.fox.dom.DOM;
import net.foxopen.fox.dom.DOMList;
import net.foxopen.fox.ex.ExInternal;
import net.foxopen.fox.module.DisplayMode;
import net.foxopen.fox.module.DisplayOrderSortable;
import net.foxopen.fox.module.HelpDisplayOption;
import net.foxopen.fox.module.LayoutDirection;
import net.foxopen.fox.module.MandatoryDisplayOption;
import net.foxopen.fox.module.OutputDescription;
import net.foxopen.fox.module.OutputError;
import net.foxopen.fox.module.OutputHint;
import net.foxopen.fox.module.OutputHistory;
import net.foxopen.fox.module.evaluatedattributeresult.BooleanAttributeResult;
import net.foxopen.fox.module.evaluatedattributeresult.DOMAttributeResult;
import net.foxopen.fox.module.evaluatedattributeresult.DOMListAttributeResult;
import net.foxopen.fox.module.evaluatedattributeresult.EvaluatedBufferAttributeResult;
import net.foxopen.fox.module.evaluatedattributeresult.FixedStringAttributeResult;
import net.foxopen.fox.module.evaluatedattributeresult.StringAttributeResult;
import net.foxopen.fox.module.fieldset.fieldmgr.FieldMgr;
import net.foxopen.fox.module.parsetree.EvaluatedParseTree;
import net.foxopen.fox.module.parsetree.evaluatedpresentationnode.EvaluatedPresentationNode;
import net.foxopen.fox.module.parsetree.evaluatedpresentationnode.GenericAttributesEvaluatedPresentationNode;
import net.foxopen.fox.module.parsetree.evaluatedpresentationnode.behaviour.EvaluatedPresentationNodeBehaviour;
import net.foxopen.fox.module.parsetree.presentationnode.GenericAttributesPresentationNode;
import net.foxopen.fox.module.parsetree.presentationnode.PresentationNode;
import net.foxopen.fox.module.serialiser.widgets.WidgetBuilderType;
import net.foxopen.fox.module.serialiser.widgets.WidgetType;
import net.foxopen.fox.track.Track;
import net.foxopen.fox.track.TrackFlag;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public abstract class EvaluatedNode
implements DisplayOrderSortable {

  private final EvaluatedNode mParent;
  private final NodeEvaluationContext mNodeEvaluationContext;
  private final GenericAttributesEvaluatedPresentationNode<? extends GenericAttributesPresentationNode> mEvaluatedPresentationNode;
  private NodeVisibility mVisibility;

  // Variables to cache possible work JIT
  private OutputHint mHint;
  private OutputError mError;
  private OutputDescription mDescription;
  private WidgetBuilderType mWidgetType;
  private StringAttributeResult mPrompt;
  private Boolean mIsRunnable;

  protected EvaluatedNode(EvaluatedNode pParent, GenericAttributesEvaluatedPresentationNode<? extends GenericAttributesPresentationNode> pEvaluatedPresentationNode,
                          NodeEvaluationContext pNodeEvaluationContext, NodeVisibility pNodeVisibility)
  throws ExInternal {
    mParent = pParent;
    mEvaluatedPresentationNode = pEvaluatedPresentationNode;
    mNodeEvaluationContext = pNodeEvaluationContext;
    mVisibility = pNodeVisibility;
  }

  /**
   * Get filtered list of namespaces compiled from mode/view rules, parents edit/ro etc.
   *
   * @return List of namespaces that are "on" for this element with most important first
   */
  public List<String> getNamespacePrecedenceList() {
    return mNodeEvaluationContext.getNamespacePrecedenceList();
  }

  public boolean isInRadioGroup() {
    return getStringAttribute(NodeAttribute.RADIO_GROUP) != null;
  }

  public boolean isMultiSelect() {
    return !".".equals(getStringAttribute(NodeAttribute.SELECTOR, "."));
  }

  /**
   * Checks the xpath of the run attribute on the item which has a run which has a valid mode in the namespace
   * (e.g. xxx:run="." would be runnable with xxx:mode=".")
   **/
  public boolean isRunnable() {
    //Use the cached result if available
    if(mIsRunnable != null){
      return mIsRunnable;
    }

    if (mNodeEvaluationContext.hasNodeAttribute(NodeInfo.FOX_NAMESPACE, "run") && !mNodeEvaluationContext.getModeList().contains(NodeInfo.FOX_NAMESPACE)) {
      Track.alert("fox:run found on element (" + getIdentityInformation() + "), this should probably under a specific namespace, it won't have any effect without a fox:mode on the set-out");
    }

    mIsRunnable = mNodeEvaluationContext.checkCachedNamespaceListFunction(NodeEvaluationContext.NamespaceListType.MODE, NamespaceFunctionAttribute.RUN).attributeValue();

    // Validate, possibly have module load time parsing check for runs with no action
    if(mIsRunnable && XFUtil.isNull(getActionName())) {
      Track.alert("RunnableWithoutAction", "Runnable element (" + getIdentityInformation() + ") does not have any action defined", TrackFlag.BAD_MARKUP);
    }

    return mIsRunnable;
  }

  public final WidgetBuilderType getWidgetBuilderType() {
    if (mWidgetType != null) {
      return mWidgetType;
    }

    mWidgetType = getWidgetTypeInternal().getBuilderType();
    return mWidgetType;
  }

  public boolean isPlusWidget() {
    return getWidgetTypeInternal().isIsPlusWidget();
  }

  public boolean isWidgetAutoResize() {
    return getBooleanAttribute(NodeAttribute.AUTO_RESIZE, false) || "input-resizable".equals(getStringAttribute(NodeAttribute.WIDGET));
  }

  /**
   * Check if the element is marked up as a "phantom" type
   *
   * @return true if schema type attribute for the element is "phantom"
   */
  public abstract boolean isPhantom();


  /**
   * Tests if the given attribute is defined on this EvaluatedNode for at least one active namespace.
   * @param pAttr
   * @return True if a value exists for the given attribute.
   */
  public boolean isAttributeDefined(NodeAttribute pAttr) {
    return mNodeEvaluationContext.isAttributeDefined(pAttr);
  }

  /**
   * Get a StringAttributeResult for a given NodeAttribute or null if the attribute wasn't defined
   *
   * @param pAttr
   * @return StringAttributeResult or null if no attribute defined
   */
  public StringAttributeResult getStringAttributeResultOrNull(NodeAttribute pAttr) {
    return mNodeEvaluationContext.getStringAttributeOrNull(pAttr);
  }

  /**
   * Get raw unescaped string value for an attribute, evaluated if the NodeAttribute defines it as evaluatable, or the default if no
   * Attribute found.
   * <strong>Do not use this when directly embedding the attribute value in output, use getStringAttributeResultOrNull() and escape
   * where necessary.</strong>
   *
   * @param pAttr
   * @param pDefault
   * @return
   */
  public String getStringAttribute(NodeAttribute pAttr, String pDefault) {
    String lAttrValue = getStringAttribute(pAttr);
    //Note: attr value may be empty string - this should be passed back
    return lAttrValue != null ? lAttrValue : pDefault;
  }

  /**
   * Get raw unescaped string value for an attribute, evaluated if the NodeAttribute defines it as evaluatable.
   * <strong>Do not use this when directly embedding the attribute value in output, use getStringAttributeResultOrNull() and escape
   * where necessary.</strong>
   *
   * @param pAttr
   * @return null if no attribute defined
   */
  public String getStringAttribute(NodeAttribute pAttr) {
    StringAttributeResult lStringAttributeResult = mNodeEvaluationContext.getStringAttributeOrNull(pAttr);
    return lStringAttributeResult==null?null:lStringAttributeResult.getString();
  }

  /**
   * Return a list of the <strong>unescaped</strong> string attributes
   *
   * @param pNodeAttributes
   * @return A list containing the result of whatever attributes were asked for
   */
  public List<String> getStringAttributes(NodeAttribute... pNodeAttributes) {
    return mNodeEvaluationContext.getStringAttributes(pNodeAttributes);
  }

  /**
   * Gets the boolean value for an attribute, evaluated if the NodeAttribute defines it as evaluatable or as Boolean.valueOf if not.
   * @param pAttr
   * @return Boolean value of attribute, or null if no attribute defined.
   */
  public BooleanAttributeResult getBooleanAttributeOrNull(NodeAttribute pAttr) {
    return mNodeEvaluationContext.getBooleanAttributeOrNull(pAttr);
  }

  /**
   * Gets the boolean value for an attribute, evaluated if the NodeAttribute defines it as evaluatable or as Boolean.valueOf if not.
   * @param pAttr
   * @param pDefault
   * @return Boolean value of attribute, or pDefault if no attribute defined.
   */
  public Boolean getBooleanAttribute(NodeAttribute pAttr, boolean pDefault) {
    BooleanAttributeResult lBooleanAttributeResult = getBooleanAttributeOrNull(pAttr);
    if (lBooleanAttributeResult != null) {
      return lBooleanAttributeResult.getBoolean();
    }
    else {
      return pDefault;
    }
  }

  /**
   * Get DOM value for an attribute, evaluated if the NodeAttribute defines it as evaluatable
   *
   * @param pAttr
   * @return null if no attribute defined
   */
  public DOMAttributeResult getDOMAttributeOrNull(NodeAttribute pAttr) {
    return mNodeEvaluationContext.getDOMAttributeOrNull(pAttr);
  }

  /**
   * Get DOMList value for an attribute, evaluated if the NodeAttribute defines it as evaluatable
   *
   * @param pAttr
   * @return null if no attribute defined
   */
  public DOMListAttributeResult getDOMListAttributeOrNull(NodeAttribute pAttr) {
    return mNodeEvaluationContext.getDOMListAttributeOrNull(pAttr);
  }

  /**
   * Get EvaluiatedBuffer for an attribute, evaluated if the NodeAttribute defines it as evaluatable
   *
   * @param pAttr
   * @return null if no attribute defined
   */
  public EvaluatedBufferAttributeResult getEvaluatedBufferAttributeOrNull(NodeAttribute pAttr) {
    return mNodeEvaluationContext.getEvaluatedBufferAttributeOrNull(pAttr);
  }

  /**
   * Get a summary prompt, for use in list column headings, using prompt-short or falling back
   *
   * @return Summary Prompt
   */
  protected StringAttributeResult getSummaryPromptInternal() {
    // 1. prompt-short
    StringAttributeResult promptShortValue = getStringAttributeResultOrNull(NodeAttribute.PROMPT_SHORT);

    // If prompt-short exists but is empty string, return null (no summary prompt)
    if (promptShortValue != null && "".equals(promptShortValue.getString())) {
      return new FixedStringAttributeResult("");
    }

    // 2. prompt
    StringAttributeResult promptValue = getStringAttributeResultOrNull(NodeAttribute.PROMPT);

    // If no prompt-short, determine whether to disable column heading using prompt or not
    if (promptShortValue == null && promptValue != null && "".equals(promptValue.getString())) {
      return new FixedStringAttributeResult("");
    }

    // Display a column heading
    if (promptShortValue != null || promptValue != null) {
      // If prompt-short has a value, it takes precedence, otherwise fall back to prompt
      if (promptShortValue != null) {
        promptValue = promptShortValue;
      }

      if (XFUtil.exists(promptValue.getString())) {
        return promptValue;
      }
      // xpath returns empty string so hide the column prompt
      else {
        return new FixedStringAttributeResult("");
      }
    }
    else {
      // If there was no prompt-short or prompt, return null
      // So individual getSummaryPrompt() impl can see the prompt wasn't set to empty and carry on looking for one
      return null;
    }
  }

  /**
   * Returns the summary prompt, with or without XPath (NB: XPath must be an absolute reference to a single node.
   * Note this method always returns an object, however the prompt may be null within the PromptResult.
   */
  public abstract StringAttributeResult getSummaryPrompt();

  public abstract String getPromptInternal();

  /**
   * Get the default prompt value for this node, using the prompt or prompt-short attribute, as a StringAttributeResult
   *
   * @return Prompt value
   */
  private StringAttributeResult getDefaultPrompt() {
    // Prompt
    StringAttributeResult lPromptValue = getStringAttributeResultOrNull(NodeAttribute.PROMPT);
    if (lPromptValue == null) {
      // If no prompt attr, try prompt short
      lPromptValue = getStringAttributeResultOrNull(NodeAttribute.PROMPT_SHORT);
    }

    return lPromptValue;
  }

  /**
   * Get prompt value for this node using the default prompt value or asking the EvaluatedNode instance for a specific
   * prompt based on its type
   *
   * @return Prompt Value
   */
  public StringAttributeResult getPrompt() {
    if (mPrompt != null) {
      return mPrompt;
    }

    StringAttributeResult lPromptValue = getDefaultPrompt();

    if (lPromptValue == null) {
      lPromptValue = new FixedStringAttributeResult(getPromptInternal());
    }

    mPrompt = lPromptValue;
    return mPrompt;
  }

  /**
   * Does this element actually have a prompt?
   *
   * @return true if the prompt is set to something that resolves to more than one character
   */
  public boolean hasPrompt() {
    return (getPrompt()!= null && !XFUtil.isNull(getPrompt().getString()));
  }

  /**
   * Get the Evaluated Presentation Node for a prompt-buffer if ones exists or fall back to the prompt-short-buffer
   * in the same way that getPrompt will fall back to prompt-short by default
   *
   * @return EvaluatedPresentationNode of the buffer to serialise for a prompt
   */
  public EvaluatedPresentationNode<? extends PresentationNode> getPromptBuffer() {
    EvaluatedBufferAttributeResult lEvaluatedBufferAttributeResult = getEvaluatedBufferAttributeOrNull(NodeAttribute.PROMPT_BUFFER);
    if (lEvaluatedBufferAttributeResult == null) {
      lEvaluatedBufferAttributeResult = getEvaluatedBufferAttributeOrNull(NodeAttribute.PROMPT_SHORT_BUFFER);
    }

    if (lEvaluatedBufferAttributeResult != null) {
      return lEvaluatedBufferAttributeResult.getEvaluatedBuffer();
    }
    return null;
  }

  /**
   * Get the Evaluated Presentation Node for a prompt-short-buffer if ones exists or fall back to the prompt-buffer
   * in the same way that getSummaryPrompt will fall back to prompt by default
   *
   * @return EvaluatedPresentationNode of the buffer to serialise for a prompt summary (column header)
   */
  public EvaluatedPresentationNode<? extends PresentationNode> getPromptSummaryBuffer() {
    EvaluatedBufferAttributeResult lEvaluatedBufferAttributeResult = getEvaluatedBufferAttributeOrNull(NodeAttribute.PROMPT_SHORT_BUFFER);
    if (lEvaluatedBufferAttributeResult == null) {
      lEvaluatedBufferAttributeResult = getEvaluatedBufferAttributeOrNull(NodeAttribute.PROMPT_BUFFER);
    }

    if (lEvaluatedBufferAttributeResult != null) {
      return lEvaluatedBufferAttributeResult.getEvaluatedBuffer();
    }
    return null;
  }

  /**
   * Gets the name of the underlying element or action depending on implementation
   *
   * @return element name
   */
  @Override
  public abstract String getName();

  /**
   * Get name of action from the schema/action depending on implementation
   * @return
   */
  public abstract String getActionName();

  /**
   * Get the DOM ref for the action
   *
   * @return
   */
  public String getActionContextRef() {
    DOMAttributeResult lActionContextDOM = getDOMAttributeOrNull(NodeAttribute.ACTION_CONTEXT_DOM);
    if (lActionContextDOM != null && lActionContextDOM.getDOM() != null) {
      return lActionContextDOM.getDOM().getRef();
    }
    return mNodeEvaluationContext.getActionContextDOM().getRef();
  }

  public String getChangeActionName() {
    return getStringAttribute(NodeAttribute.CHANGE_ACTION);
  }

  public String getFieldWidth() {
    String lFieldWidth = getStringAttribute(NodeAttribute.FIELD_WIDTH, "auto");
    if ("auto".equals(lFieldWidth)) {
      // If no width was specified and it defaulted to auto, determine the width as best we can automatically
      lFieldWidth = getAutoFieldWidth();
      // Then make sure that automatic value falls within some sensible min/max boundaries
      Integer lIntWidth = Integer.valueOf(lFieldWidth);
      Integer lIntMinWidth = Integer.valueOf(getStringAttribute(NodeAttribute.FIELD_MIN_WIDTH, "1"));
      Integer lIntMaxWidth = Integer.valueOf(getStringAttribute(NodeAttribute.FIELD_MAX_WIDTH, "80"));
      lIntWidth = Math.min(Math.max(lIntWidth, lIntMinWidth), lIntMaxWidth);
      return lIntWidth.toString();
    }
    else {
      return lFieldWidth;
    }
  }

  protected String getAutoFieldWidth() {
    return "20";
  }

  public String getFieldHeight() {
    String lFieldHeight = getStringAttribute(NodeAttribute.FIELD_HEIGHT, "auto");
    if ("auto".equals(lFieldHeight)) {
      // If no height was specified and it defaulted to auto, determine the height as best we can automatically
      lFieldHeight = getAutoFieldHeight();
      // Then make sure that automatic value falls within some sensible min/max boundaries
      Integer lIntHeight = Integer.valueOf(lFieldHeight);
      Integer lIntMinHeight = Integer.valueOf(getStringAttribute(NodeAttribute.FIELD_MIN_HEIGHT, "1"));
      Integer lIntMaxHeight = Integer.valueOf(getStringAttribute(NodeAttribute.FIELD_MAX_HEIGHT, "10"));
      lIntHeight = Math.min(Math.max(lIntHeight, lIntMinHeight), lIntMaxHeight);
      return lIntHeight.toString();
    }
    else {
      return lFieldHeight;
    }
  }

  protected String getAutoFieldHeight() {
    return "1";
  }

  /**
   * Get the maximum length, in characters, of data that is allowed for this node
   *
   * @return
   */
  public Integer getMaxDataLength() {
    throw new ExInternal("Only EvaluatedNodeInfo elements can have a maximum length: " + getIdentityInformation());
  }

  private WidgetType getWidgetTypeInternal() {
    WidgetType lWidgetType = getWidgetType();
    if(lWidgetType == null) {
      throw new ExInternal("Invalid widget (" + getStringAttribute(NodeAttribute.WIDGET) + ") defined on node " + getIdentityInformation());
    }

    //If this is the default action, check it's valid and switch it to a SUBMIT widget
    if(getBooleanAttribute(NodeAttribute.DEFAULT_ACTION, false)) {
      //TODO move to FieldSet?
      if (mNodeEvaluationContext.getEvaluatedParseTree().getDefaultAction() != null) {
        throw new ExInternal("Attempted to set " + getActionName() + " as a default action when " +
          mNodeEvaluationContext.getEvaluatedParseTree().getDefaultAction().getActionName() + " has already been declared default. You may only have one default action.");
      }
      else {
        mNodeEvaluationContext.getEvaluatedParseTree().setDefaultAction(this);
      }

      lWidgetType = WidgetType.fromBuilderType(WidgetBuilderType.SUBMIT);
    }

    return lWidgetType;
  }

  protected abstract WidgetType getWidgetType();

  /**
   * Gets the hint text for this node, by default from the "hint" attribute. This method should be overloaded if subclasses
   * have an alternative default hint.
   * @return
   */
  protected StringAttributeResult getDefaultHint() {
    return getStringAttributeResultOrNull(NodeAttribute.HINT);
  }

  /**
   * Get an OutputHint wrapper for the hint information on this element given title and hint content
   *
   * @return Hint information wrapper
   */
  public OutputHint getHint() {
    if (mHint != null) {
      return mHint;
    }

    StringAttributeResult lHintText = getDefaultHint();
    EvaluatedBufferAttributeResult lEvaluatedBufferAttributeOrNull = getEvaluatedBufferAttributeOrNull(NodeAttribute.HINT_BUFFER);
    EvaluatedPresentationNode<? extends PresentationNode> lEvaluatedBuffer = null;
    if (lEvaluatedBufferAttributeOrNull != null) {
      lEvaluatedBuffer = lEvaluatedBufferAttributeOrNull.getEvaluatedBuffer();
    }



    //If a description is defined and is set to ICON display mode, it should be concatenated in front of the hint
    OutputDescription lDescription = null;
    if(HelpDisplayOption.fromExternalString(getStringAttribute(NodeAttribute.DESCRIPTION_DISPLAY)) == HelpDisplayOption.ICON) {
      lDescription = getDescription();
    }

    if ((lHintText != null && XFUtil.exists(lHintText.getString())) || lEvaluatedBuffer != null) {
      StringAttributeResult lHintTitle = getStringAttributeResultOrNull(NodeAttribute.HINT_TITLE);

      // If it's an action type widget, no NodeAttribute.HINT_TITLE was defined and it has a prompt, use that for the hint title
      if (getWidgetBuilderType().isAction() && lHintTitle == null && hasPrompt()) {
        lHintTitle = getPrompt();
      }

      String lHintID = "hint" + mNodeEvaluationContext.getEvaluatedParseTree().getFieldSet().getNextFieldSequence();
      mHint = new OutputHint(lHintID, lHintTitle, lHintText, lEvaluatedBuffer, lDescription, getStringAttribute(NodeAttribute.HINT_URL));
    }

    return mHint;
  }

  public boolean hasHint() {
    return (getHint() != null && DisplayMode.isDisplayAllowed(this, NodeAttribute.HINT_DISPLAY_MODE));
  }

  protected StringAttributeResult getDefaultDescription() {
    return getStringAttributeResultOrNull(NodeAttribute.DESCRIPTION);
  }

  public OutputDescription getDescription() {
    if (mDescription != null) {
      return mDescription;
    }

    StringAttributeResult lDescriptionContent = getDefaultDescription();
    EvaluatedBufferAttributeResult lEvaluatedBufferAttributeOrNull = getEvaluatedBufferAttributeOrNull(NodeAttribute.DESCRIPTION_BUFFER);
    EvaluatedPresentationNode<? extends PresentationNode> lEvaluatedBuffer = null;
    if (lEvaluatedBufferAttributeOrNull != null) {
      lEvaluatedBuffer = lEvaluatedBufferAttributeOrNull.getEvaluatedBuffer();
    }


    if((lDescriptionContent != null && XFUtil.exists(lDescriptionContent.getString())) || lEvaluatedBuffer != null) {
      mDescription = new OutputDescription(getDefaultDescription(), lEvaluatedBuffer);
    }

    return mDescription;
  }

  public boolean hasDescription() {
    boolean lShowDescriptionInline = HelpDisplayOption.fromExternalString(getStringAttribute(NodeAttribute.DESCRIPTION_DISPLAY, HelpDisplayOption.INLINE.getExternalString())) == HelpDisplayOption.INLINE;
    return (getDescription() != null && lShowDescriptionInline && DisplayMode.isDisplayAllowed(this, NodeAttribute.DESCRIPTION_DISPLAY_MODE));
  }

  /**
   * Get the layout position of the description relative to the field. Defaults to LayoutDirection.SOUTH
   *
   * @return Layout direction of the description relative to the field
   */
  public LayoutDirection getDescriptionLayout() {
    return LayoutDirection.valueOf(getStringAttribute(NodeAttribute.DESCRIPTION_LAYOUT, "south").toUpperCase());
  }

  public boolean isEnableFocusHintDisplay() {
    return getBooleanAttribute(NodeAttribute.ENABLE_FOCUS_HINT_DISPLAY, false);
  }

  /**
   * Get an OutputError wrapper for the error information on this element
   *
   * @return Error information wrapper
   */
  public OutputError getError(){
    if (mError != null) {
      return mError;
    }

    if(mNodeEvaluationContext.getDataItem() != null) {
      DOMList lErrorList = mNodeEvaluationContext.getDataItem().getUL("fox-error/msg");
      int lErrorCount = lErrorList.getLength();

      if(lErrorCount != 0) {
        StringBuilder lErrorOutputMessage = new StringBuilder(200);
        if(lErrorCount == 1) {
          lErrorOutputMessage.append(lErrorList.item(0).value(false));
        }
        else {
          lErrorOutputMessage.append(lErrorCount);
          lErrorOutputMessage.append(" Errors: ");
          for(int i = 0; i < lErrorCount; i++) {
            lErrorOutputMessage.append(" (");
            lErrorOutputMessage.append(i+1);
            lErrorOutputMessage.append(") ");
            lErrorOutputMessage.append(lErrorList.item(i).value(false));
          }
        }

        mError = new OutputError(lErrorOutputMessage.toString(), getStringAttribute(NodeAttribute.ERROR_URL), getStringAttribute(NodeAttribute.ERROR_URL_PROMPT));
      }
    }

    return mError;
  }

  public boolean hasError() {
    return (getError() != null);
  }

  /**
   * Get an OutputHistory wrapper for the fox-history information on this element (typically created by the fm:compare command)
   *
   * @return History information for this node
   */
  public OutputHistory getHistory() {
    if(mNodeEvaluationContext.getDataItem() != null && mNodeEvaluationContext.getDataItem().get1EOrNull("fox-history") != null) {
      String lLabel = mNodeEvaluationContext.getDataItem().get1SNoEx("fox-history/history/label");
      String lOperation = mNodeEvaluationContext.getDataItem().get1SNoEx("fox-history/history/operation");
      String lValue = mNodeEvaluationContext.getDataItem().get1SNoEx("fox-history/history/value");
      return new OutputHistory(lLabel, lOperation, lValue);
    }
    return null;
  }

  public boolean hasHistory() {
    // History should not be displayed for widgets which are marked internal (cellmates, forms, lists etc.)
    boolean lDisableHistoryDisplay = !getWidgetBuilderType().isInternalOnly();

    return (lDisableHistoryDisplay && getHistory() != null);
  }

  /**
   * Gets the prefix to display read-only before an input
   * @return Prefix for this node
   */
  public StringAttributeResult getPrefix() {
    return getStringAttributeResultOrNull(NodeAttribute.FIELD_PREFIX);
  }

  /**
   * Gets the suffix to display read-only after an input
   * @return Suffix for this node
   */
  public StringAttributeResult getSuffix() {
    return getStringAttributeResultOrNull(NodeAttribute.FIELD_SUFFIX);
  }

  /**
   * By default all nodes are not mandatory, though implementations with a NodeInfo might provide a non-false result
   * depending on attributes on the element and the type of field.
   *
   * @return
   */
  public boolean isMandatory() {
    return false;
  }

  /**
   * Get an enum value from MandatoryDisplayOption defining what to show for mandatoryness. Potentially pulled down to
   * NONE if the display of mandatoryness is not allowed by NodeAttribute.MANDATORY_DISPLAY_MODE
   *
   * @return MandatoryDisplayOption defining what to show based on fields mandatoryness
   */
  public MandatoryDisplayOption getMandatoryDisplay() {
    if (!DisplayMode.isDisplayAllowed(this, NodeAttribute.MANDATORY_DISPLAY_MODE, DisplayMode.EDIT)) {
      return MandatoryDisplayOption.NONE;
    }

    return MandatoryDisplayOption.fromString(getStringAttribute(NodeAttribute.MANDATORY_DISPLAY, "mandatory"));
  }

  public String getConfirmMessage() {
    return getStringAttribute(NodeAttribute.CONFIRM);
  }

  /**
   * Check the nodes going up the EvaluatedNode tree to see if any of them are of a given WidgetBuilderType
   *
   * @param pWidgetBuilderTypes One or more
   * @return
   */
  public boolean checkAncestry(WidgetBuilderType... pWidgetBuilderTypes) {
    if (pWidgetBuilderTypes.length == 0) {
      throw new ExInternal("You need to pass at least one widget builder to check ancestry");
    }

    EvaluatedNode lParent = getParent();
    if (lParent == null) {
      return false;
    }
    else {
      for (WidgetBuilderType lWidgetBuilderType : pWidgetBuilderTypes) {
        if (lParent.getWidgetBuilderType() == lWidgetBuilderType || lParent.checkAncestry(lWidgetBuilderType)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Tests whether the node is nested within either a form or list, i.e. it has a form or list ancestor
   * @return True if the node is nested within a form or list, false if it is not
   */
  public boolean isNested() {
    return checkAncestry(WidgetBuilderType.FORM, WidgetBuilderType.LIST);
  }

  /**
   * Get a string back to help developers identify this EvaluatedNode
   * Combines prompt, element name and action name to try and show something identifiable
   *
   * @return String to help identify this node
   */
   public String getIdentityInformation() {
     StringBuilder lReturnValue = new StringBuilder();
     lReturnValue.append(getClass().getSimpleName());
     lReturnValue.append("[");

     String lPrompt = getPrompt().getString();
     if (lPrompt != null) {
       lReturnValue.append("Prompt: '");
       lReturnValue.append(lPrompt);
       lReturnValue.append("'");
     }

     String lActionName = getActionName();
     if (lActionName != null) {
       lReturnValue.append(", ");
       lReturnValue.append("Action: '");
       lReturnValue.append(lActionName);
       lReturnValue.append("'");
     }

     if (getEvaluatedPresentationNode() != null) {
       lReturnValue.append(", ");
       lReturnValue.append("PresentationNode: '");
       lReturnValue.append(getEvaluatedPresentationNode().toString());
       lReturnValue.append("'");
     }

     lReturnValue.append("]");
     return lReturnValue.toString();
   }

  @Override
  public String toString() {
    return getIdentityInformation();
  }

  public EvaluatedNode getParent() {
    return mParent;
  }

  public NodeEvaluationContext getNodeEvaluationContext() {
    return mNodeEvaluationContext;
  }

  public NodeVisibility getVisibility() {
    return mVisibility;
  }

  protected void setVisibility(NodeVisibility pNewVisibility) {
    mVisibility = pNewVisibility;
  }

  //This method should not be exposed out of this package! Serialisers should not be able to get access to it.
  protected EvaluatedParseTree getEvaluatedParseTree() {
    return mNodeEvaluationContext.getEvaluatedParseTree();
  }

  public ContextUElem getContextUElem() {
    return mNodeEvaluationContext.getContextUElem();
  }

  public DOM getDataItem() {
    return mNodeEvaluationContext.getDataItem();
  }

  public DOM getEvaluateContextRuleItem() {
    return mNodeEvaluationContext.getEvaluateContextRuleItem();
  }

  public GenericAttributesEvaluatedPresentationNode<? extends GenericAttributesPresentationNode> getEvaluatedPresentationNode() {
    return mEvaluatedPresentationNode;
  }

  /**
   * Casts this node's EvaluatedPresentationNode (EPN) to a behaviour implementation of the given type. Use this for
   * nodes which need specified behaviours which do not form part of the standard EPN contract. If the EPN cannot be
   * cast to the required behaviour, an error is raised.
   * @param <T>
   * @param pClass Desired behaviour.
   * @return The EPN cast to the desired behaviour.
   */
  public <T extends EvaluatedPresentationNodeBehaviour> T getEPNBehaviour(Class<T> pClass) {
    T lResult = getEPNBehaviourOrNull(pClass);
    if(lResult == null) {
      throw new ExInternal("This EvaluatedNode's EvaluatedPresentationNode is not an instance of " + pClass.getSimpleName());
    }
    return lResult;
  }

  /**
   * Same as {@link #getEPNBehaviour} except this returns null instead of erroring if the EPN does not perform the
   * desired behaviour.
   * @param <T>
   * @param pClass
   * @return The EPN cast to the desired behaviour, or null.
   */
  public final <T extends EvaluatedPresentationNodeBehaviour> T getEPNBehaviourOrNull(Class<T> pClass) {
    if(pClass.isAssignableFrom(mEvaluatedPresentationNode.getClass())) {
      return pClass.cast(mEvaluatedPresentationNode);
    }
    else {
      return null;
    }
  }

  public abstract FieldMgr getFieldMgr();

  public abstract String getExternalFieldName();

  public abstract int getSelectorMaxCardinality();

  @Override
  public String getDisplayBeforeAttribute() {
    return getStringAttribute(NodeAttribute.DISPLAY_BEFORE);
  }

  @Override
  public String getDisplayAfterAttribute() {
    return getStringAttribute(NodeAttribute.DISPLAY_AFTER);
  }

  @Override
  public String getDisplayOrder() {
    return getStringAttribute(NodeAttribute.DISPLAY_ORDER, "auto");
  }

  /**
   * Returns whether the node should be initially displayed in the output. A client visibility rule for example may
   * set the node to be initially hidden which may be layer toggled to displayed after the output has been serialised.
   * Note this differs to the visibility of the node, as visibility determines whether or not the node should be
   * included in the output at all, and cannot be toggled after serialisation.
   * @return True if the node should be initially displayed in the output, false if it should be hidden
   */
  public boolean isInitiallyDisplayed() {
    return true;
  }

  /**
   * Classes which need to be added to the containing "cell" of this evaluated node.
   * @return
   */
  public Collection<String> getCellInternalClasses(){
    return Collections.emptySet();
  }

  /**
   * Data attributes which need to be added to the containing "cell" of this evaluated node.
   * @return
   */
  public Map<String, String> getCellInternalAttributes() {
    return Collections.emptyMap();
  }
}
